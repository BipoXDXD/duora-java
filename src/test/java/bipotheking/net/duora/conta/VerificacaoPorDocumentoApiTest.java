package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ConferenciaDeDocumentoDeTeste.Chamada;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDaProva;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import bipotheking.net.duora.conta.dominio.SituacaoDaProva;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * API do envio do documento na faixa de dúvida (D-B), com os dublês programáveis no lugar dos
 * falsos. Cada teste parte de uma conta nascida em 1990-05-10 com a selfie em dúvida (17–24) e uma
 * sessão {@code DOCUMENTO} aberta.
 */
@Import(DublesDeVerificacao.class)
class VerificacaoPorDocumentoApiTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final String MARCADOR_DA_FRENTE = "frente-de-teste";
  private static final String MARCADOR_DO_VERSO = "verso-de-teste";
  private static final String MARCADOR_DA_REFERENCIA = "referencia-do-documento";
  private static final byte[] FRENTE = jpeg(MARCADOR_DA_FRENTE);
  private static final byte[] VERSO = jpeg(MARCADOR_DO_VERSO);
  private static final String CORPO_SELFIE =
      "{\"consentimentoBiometrico\":true,\"finalidade\":\"SELFIE\"}";
  private static final String CORPO_DOCUMENTO =
      "{\"consentimentoBiometrico\":true,\"finalidade\":\"DOCUMENTO\"}";
  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private ContasDeTeste contas;
  @Autowired private ProvaDeVidaDeTeste provaDeVida;
  @Autowired private ConferenciaDeDocumentoDeTeste conferencia;

  @BeforeEach
  void zerarDubles() {
    provaDeVida.zerar();
    conferencia.zerar();
  }

  @Test
  void documentoConferidoAprova() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    Imagem referencia = imagem(MARCADOR_DA_REFERENCIA);
    provaDeVida.programarPorSessao(
        preparo.documento(),
        new ResultadoDaProva(
            SituacaoDaProva.CONCLUIDA,
            99.0,
            Optional.of(new FaixaEstimada(17, 24)),
            Optional.of(referencia)));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(
            content()
                .json(
                    "{\"resultado\":\"APROVADA\",\"pedeDocumento\":false,"
                        + "\"tentativasRestantesHoje\":2,\"enviosDeDocumentoRestantesHoje\":1}",
                    JsonCompareMode.STRICT));

    assertThat(estadoDe(preparo.conta())).isEqualTo("ATIVA");
    assertThat(
            jdbc.sql("select metodo from verificacao_idade where conta_id = :conta")
                .param("conta", preparo.conta().id().valor())
                .query(String.class)
                .list())
        .containsExactly("DOCUMENTO");
    Chamada chamada = conferencia.ultimaChamada().orElseThrow();
    assertThat(chamada.frente().bytes()).isEqualTo(FRENTE);
    assertThat(chamada.verso().bytes()).isEqualTo(VERSO);
    assertThat(chamada.selfie().bytes()).isEqualTo(referencia.bytes());
  }

  @Test
  void dataDiferenteFicaEmDuvida() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.programar(
        new ResultadoDoDocumento(true, Optional.of(LocalDate.of(1991, 5, 10)), true));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("DUVIDA"))
        .andExpect(jsonPath("$.pedeDocumento").value(false));

    assertThat(estadoDe(preparo.conta())).isEqualTo("AGUARDANDO_VERIFICACAO");
  }

  @Test
  void rostoQueNaoConfereFicaEmDuvida() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.programar(
        new ResultadoDoDocumento(true, Optional.of(LocalDate.of(2008, 11, 23)), false));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("DUVIDA"));

    assertThat(estadoDe(preparo.conta())).isEqualTo("AGUARDANDO_VERIFICACAO");
    assertThat(contar("select count(*) from email_bloqueado")).isZero();
  }

  @Test
  void documentoDeMenorSegueOFluxoDoMenor() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.programar(
        new ResultadoDoDocumento(true, Optional.of(LocalDate.of(2011, 11, 22)), true));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MENOR_DE_IDADE"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    assertThat(estadoDe(preparo.conta())).isEqualTo("EXCLUIDA");
    assertThat(contar("select count(*) from email_bloqueado where motivo = 'MENOR'")).isOne();
    assertThat(contar("select count(*) from sessao")).isZero();
    assertThat(contar("select count(*) from verificacao_idade")).isZero();
  }

  @Test
  void documentoIlegivelPedeRepetir() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.programar(new ResultadoDoDocumento(false, Optional.empty(), false));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("REPETIR"))
        .andExpect(jsonPath("$.pedeDocumento").value(false))
        .andExpect(jsonPath("$.enviosDeDocumentoRestantesHoje").value(1));
  }

  /** D14-bis: a prova abaixo de 90,0 encerra a tentativa e gasta o envio do dia. */
  @Test
  void confiancaBaixaNaoChamaADiditEGastaOEnvio() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    provaDeVida.programarPorSessao(
        preparo.documento(), ProvaDeVidaDeTeste.concluida(80.0, new FaixaEstimada(30, 40)));

    exigirSelfieNovaQueEncerraATentativa(preparo);
  }

  /** D14-bis: a prova que falhou encerra a tentativa e gasta o envio do dia. */
  @Test
  void provaQueFalhouGastaOEnvio() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    provaDeVida.programarPorSessao(
        preparo.documento(),
        new ResultadoDaProva(SituacaoDaProva.FALHOU, 0.0, Optional.empty(), Optional.empty()));

    exigirSelfieNovaQueEncerraATentativa(preparo);
  }

  /** D14-bis: o envio imediato, com a prova ainda criada, encerra a tentativa e gasta o envio. */
  @Test
  void provaAindaCriadaGastaOEnvio() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    provaDeVida.programarPorSessao(
        preparo.documento(),
        new ResultadoDaProva(SituacaoDaProva.CRIADA, 0.0, Optional.empty(), Optional.empty()));

    exigirSelfieNovaQueEncerraATentativa(preparo);
  }

  /**
   * O 409 sem chamar a Didit, com a tentativa encerrada como {@code REPETIR}: o envio fica gasto, e
   * o mesmo envio repetido recebe 404, sem laço de 409. A selfie nova vem numa tentativa nova.
   */
  private void exigirSelfieNovaQueEncerraATentativa(Preparo preparo) throws Exception {
    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("SELFIE_NAO_DEU_CERTO"));

    assertThat(conferencia.chamadas()).isZero();
    assertThat(resultadoDaTentativa(preparo.documento())).isEqualTo("REPETIR");
    enviosRestantesHoje(preparo.conta())
        .andExpect(jsonPath("$.enviosDeDocumentoRestantesHoje").value(1));
    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));
  }

  /**
   * D14-bis: só a prova expirada descarta a tentativa e não gasta envio do dia (os 2 voltam a
   * caber); a sessão descartada não serve mais, e o mesmo envio repetido recebe 404.
   */
  @Test
  void sessaoExpiradaPedeSelfieNovaSemConcluir() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    provaDeVida.programarPorSessao(
        preparo.documento(),
        new ResultadoDaProva(SituacaoDaProva.EXPIRADA, 0.0, Optional.empty(), Optional.empty()));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("REFAZER_PROVA_DE_VIDA"));
    assertThat(tentativasDaSessao(preparo.documento())).isZero();
    enviosRestantesHoje(preparo.conta())
        .andExpect(jsonPath("$.enviosDeDocumentoRestantesHoje").value(2));

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));
    assertThat(conferencia.chamadas()).isZero();
  }

  /**
   * A resposta guardada na idempotência venceu: o reenvio da tentativa já concluída recebe 404 sem
   * chamar prova de vida nem conferência.
   */
  @Test
  void tentativaConcluidaSemRespostaGuardadaRecebe404SemChamarFornecedor() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.programar(
        new ResultadoDoDocumento(true, Optional.of(LocalDate.of(1991, 5, 10)), true));
    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("DUVIDA"));
    jdbc.sql("delete from chave_de_idempotencia").update();
    int leiturasAntes = provaDeVida.chamadasDeResultado();

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));

    assertThat(provaDeVida.chamadasDeResultado()).isEqualTo(leiturasAntes);
    assertThat(conferencia.chamadas()).isOne();
  }

  @Test
  void sessaoDeSelfieNoDocumentoRecebe404() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();

    enviar(preparo.conta(), preparo.selfie())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));

    assertThat(conferencia.chamadas()).isZero();
  }

  @Test
  void pdfNoLugarDaImagemRecebe422() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    int leiturasAntes = provaDeVida.chamadasDeResultado();
    byte[] pdf = "%PDF-1.7 frente-de-teste".getBytes(StandardCharsets.US_ASCII);

    enviar(preparo.conta(), preparo.documento(), pdf, VERSO)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));

    assertThat(provaDeVida.chamadasDeResultado()).isEqualTo(leiturasAntes);
    assertThat(conferencia.chamadas()).isZero();
  }

  @Test
  void imagemAcimaDe5MbRecebe422() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    byte[] grande = Arrays.copyOf(FRENTE, Imagem.TAMANHO_MAXIMO + 1);

    enviar(preparo.conta(), preparo.documento(), FRENTE, grande)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INVALIDO"));

    assertThat(conferencia.chamadas()).isZero();
  }

  @Test
  void semVersoRecebe400() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();

    mvc.perform(
            pedido(preparo.conta())
                .file(new MockMultipartFile("frente", "frente.jpg", "image/jpeg", FRENTE))
                .param("sessaoId", preparo.documento()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

    assertThat(conferencia.chamadas()).isZero();
  }

  @Test
  void corpoQueNaoEhMultipartRecebe400() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();

    mvc.perform(
            post("/api/conta/verificacao/documento")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessaoId\":\"" + preparo.documento() + "\"}")
                .cookie(cookieDe(preparo.conta())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));

    assertThat(conferencia.chamadas()).isZero();
  }

  @Test
  void diditForaDoArDeixaReenviar() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.falhar();

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL"));
    assertThat(estadoDe(preparo.conta())).isEqualTo("AGUARDANDO_VERIFICACAO");
    assertThat(tentativaSemResultado(preparo.documento())).isTrue();

    conferencia.zerar();
    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("APROVADA"));
  }

  @Test
  void awsForaDoArNoDocumentoRecebe503() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    provaDeVida.falharNoResultado();

    enviar(preparo.conta(), preparo.documento())
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("PROVA_DE_VIDA_INDISPONIVEL"));

    assertThat(conferencia.chamadas()).isZero();
    assertThat(tentativaSemResultado(preparo.documento())).isTrue();
  }

  @Test
  void terceiraSessaoDeDocumentoNoDiaRecebe429() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    iniciar(preparo.conta(), CORPO_DOCUMENTO).andExpect(status().isCreated());

    iniciar(preparo.conta(), CORPO_DOCUMENTO)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_VERIFICACAO"));
  }

  @Test
  void enviosSimultaneosChamamADiditUmaVez() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    conferencia.segurar();

    try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
      Future<MockHttpServletResponse> primeiro =
          threads.submit(
              () -> enviar(preparo.conta(), preparo.documento()).andReturn().getResponse());
      assertThat(conferencia.aguardarChamada(ESPERA)).isTrue();

      enviar(preparo.conta(), preparo.documento())
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.codigo").value("REQUISICAO_EM_ANDAMENTO"))
          .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

      conferencia.liberar();
      assertThat(primeiro.get(ESPERA.toSeconds(), TimeUnit.SECONDS).getStatus()).isEqualTo(200);
    }
    assertThat(conferencia.chamadas()).isOne();
  }

  @Test
  void contaAtivaNaoEnviaDocumento() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();
    String segunda = sessaoId(iniciar(preparo.conta(), CORPO_DOCUMENTO));
    enviar(preparo.conta(), preparo.documento()).andExpect(status().isOk());

    enviar(preparo.conta(), segunda)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_FORA_DE_ORDEM"));

    assertThat(conferencia.chamadas()).isOne();
  }

  @Test
  void conferenciaNuncaRodaEmTransacao() throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();

    enviar(preparo.conta(), preparo.documento()).andExpect(status().isOk());

    assertThat(conferencia.transacaoAtivaNasChamadas()).containsExactly(false);
    assertThat(provaDeVida.transacaoAtivaNasChamadas()).isNotEmpty().containsOnly(false);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void logNaoTrazImagemDoDocumento(CapturedOutput saida) throws Exception {
    Preparo preparo = emDuvidaComSessaoDeDocumento();

    enviar(preparo.conta(), preparo.documento()).andExpect(status().isOk());

    assertThat(saida.getOut()).contains(preparo.conta().id().toString());
    assertThat(saida.getOut() + saida.getErr())
        .doesNotContain(
            MARCADOR_DA_FRENTE,
            MARCADOR_DO_VERSO,
            ProvaDeVidaDeTeste.CONTEUDO_DA_REFERENCIA,
            NASCIMENTO.toString());
  }

  /** A conta, a sessão {@code SELFIE} que ficou em dúvida e a sessão {@code DOCUMENTO} aberta. */
  private record Preparo(ContaDeTeste conta, String selfie, String documento) {}

  private Preparo emDuvidaComSessaoDeDocumento() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO);
    provaDeVida.programar(ProvaDeVidaDeTeste.concluida(99.0, new FaixaEstimada(17, 24)));
    String selfie = sessaoId(iniciar(conta, CORPO_SELFIE));
    mvc.perform(
            post("/api/conta/verificacao/sessao/{sessaoId}/resultado", selfie)
                .header(MesmaOrigem.CABECALHO, "1")
                .cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("DUVIDA"));
    String documento = sessaoId(iniciar(conta, CORPO_DOCUMENTO));
    return new Preparo(conta, selfie, documento);
  }

  private ResultActions iniciar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        post("/api/conta/verificacao/sessao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private static String sessaoId(ResultActions sessao) throws Exception {
    String corpo =
        sessao.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return JsonPath.read(corpo, "$.sessaoId");
  }

  private ResultActions enviar(ContaDeTeste conta, String sessaoId) throws Exception {
    return enviar(conta, sessaoId, FRENTE, VERSO);
  }

  private ResultActions enviar(ContaDeTeste conta, String sessaoId, byte[] frente, byte[] verso)
      throws Exception {
    return mvc.perform(
        pedido(conta)
            .file(new MockMultipartFile("frente", "frente.jpg", "image/jpeg", frente))
            .file(new MockMultipartFile("verso", "verso.jpg", "image/jpeg", verso))
            .param("sessaoId", sessaoId));
  }

  private static MockMultipartHttpServletRequestBuilder pedido(ContaDeTeste conta) {
    MockMultipartHttpServletRequestBuilder pedido = multipart("/api/conta/verificacao/documento");
    pedido.header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta));
    return pedido;
  }

  private int tentativasDaSessao(String sessaoId) {
    return jdbc.sql("select count(*) from tentativa_verificacao where sessao_id = :sessao")
        .param("sessao", sessaoId)
        .query(Integer.class)
        .single();
  }

  private ResultActions enviosRestantesHoje(ContaDeTeste conta) throws Exception {
    return mvc.perform(get("/api/conta/verificacao").cookie(cookieDe(conta)))
        .andExpect(status().isOk());
  }

  private boolean tentativaSemResultado(String sessaoId) {
    return jdbc.sql(
            """
            select resultado is null from tentativa_verificacao where sessao_id = :sessao
            """)
        .param("sessao", sessaoId)
        .query(Boolean.class)
        .single();
  }

  private String resultadoDaTentativa(String sessaoId) {
    return jdbc.sql("select resultado from tentativa_verificacao where sessao_id = :sessao")
        .param("sessao", sessaoId)
        .query(String.class)
        .single();
  }

  private String estadoDe(ContaDeTeste conta) {
    return jdbc.sql("select estado from conta where id = :id")
        .param("id", conta.id().valor())
        .query(String.class)
        .single();
  }

  private int contar(String sql) {
    return jdbc.sql(sql).query(Integer.class).single();
  }

  /** {@code FF D8 FF E0} seguido do marcador: passa no {@link Imagem#ler}. */
  private static byte[] jpeg(String marcador) {
    byte[] conteudo = marcador.getBytes(StandardCharsets.US_ASCII);
    byte[] bytes = new byte[4 + conteudo.length];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    bytes[3] = (byte) 0xE0;
    System.arraycopy(conteudo, 0, bytes, 4, conteudo.length);
    return bytes;
  }

  private static Imagem imagem(String marcador) {
    return Imagem.ler(jpeg(marcador)).orElseThrow();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
