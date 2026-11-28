package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.dominio.FaixaEstimada;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

/** API da verificação de idade pela selfie, com os dublês programáveis no lugar dos falsos. */
@Import(DublesDeVerificacao.class)
class VerificacaoApiTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
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
  void iniciarSemConsentimentoRecebe422() throws Exception {
    ContaDeTeste conta = aguardando();

    iniciar(conta, "{\"consentimentoBiometrico\":false,\"finalidade\":\"SELFIE\"}")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("CONSENTIMENTO_BIOMETRICO_NECESSARIO"));

    assertThat(contar("select count(*) from tentativa_verificacao")).isZero();
  }

  @Test
  void iniciarComContaPendenteRecebe409() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.PENDENTE, null);

    iniciar(conta, CORPO_SELFIE)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_FORA_DE_ORDEM"));
  }

  @Test
  void iniciarComContaAtivaRecebe409() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    iniciar(conta, CORPO_SELFIE)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_FORA_DE_ORDEM"));

    assertThat(contar("select count(*) from tentativa_verificacao")).isZero();
  }

  @Test
  void iniciarDevolveSessaoCredencialEModo() throws Exception {
    ContaDeTeste conta = aguardando();

    String corpo =
        iniciar(conta, CORPO_SELFIE)
            .andExpect(status().isCreated())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.regiao").value("us-east-1"))
            .andExpect(jsonPath("$.modo").value("falso"))
            .andExpect(jsonPath("$.credencial.accessKeyId").value("ASIATESTE"))
            .andExpect(jsonPath("$.credencial.secretAccessKey").value(ProvaDeVidaDeTeste.SEGREDO))
            .andExpect(jsonPath("$.credencial.sessionToken").value(ProvaDeVidaDeTeste.SEGREDO))
            .andExpect(jsonPath("$.credencial.expiraEm").value("2026-11-22T12:15:00Z"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String sessaoId = JsonPath.read(corpo, "$.sessaoId");
    assertThat(
            jdbc.sql("select sessao_id from tentativa_verificacao where conta_id = :conta")
                .param("conta", conta.id().valor())
                .query(String.class)
                .list())
        .containsExactly(sessaoId);
  }

  /** O front calcula o desvio do relógio com este cabeçalho: ele vem do {@code Clock}. */
  @Test
  void iniciarTrazCabecalhoDate() throws Exception {
    iniciar(aguardando(), CORPO_SELFIE)
        .andExpect(status().isCreated())
        .andExpect(header().string(HttpHeaders.DATE, "Sun, 22 Nov 2026 12:00:00 GMT"));
  }

  @Test
  void quartaSessaoDoDiaRecebe429() throws Exception {
    ContaDeTeste conta = aguardando();
    for (int i = 0; i < 3; i++) {
      iniciar(conta, CORPO_SELFIE).andExpect(status().isCreated());
    }

    iniciar(conta, CORPO_SELFIE)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_VERIFICACAO"))
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "86400"));

    relogio.avancar(Duration.ofHours(24));
    iniciar(conta, CORPO_SELFIE).andExpect(status().isCreated());
  }

  @Test
  void sessoesSimultaneasNaoPassamDoLimite() throws Exception {
    ContaDeTeste conta = aguardando();
    Callable<MockHttpServletResponse> pedido =
        () -> iniciar(conta, CORPO_SELFIE).andReturn().getResponse();

    List<Integer> status = new ArrayList<>();
    try (ExecutorService threads = Executors.newFixedThreadPool(6)) {
      for (Future<MockHttpServletResponse> resposta :
          threads.invokeAll(Collections.nCopies(6, pedido))) {
        status.add(resposta.get().getStatus());
      }
    }

    assertThat(status).containsExactlyInAnyOrder(201, 201, 201, 429, 429, 429);
    assertThat(contar("select count(*) from tentativa_verificacao")).isEqualTo(3);
  }

  @Test
  void awsForaDoArNaCriacaoRecebe503() throws Exception {
    ContaDeTeste conta = aguardando();
    provaDeVida.falharNaCriacao();

    iniciar(conta, CORPO_SELFIE)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("PROVA_DE_VIDA_INDISPONIVEL"));

    assertThat(contar("select count(*) from tentativa_verificacao")).isZero();
    situacao(conta).andExpect(jsonPath("$.tentativasRestantesHoje").value(3));
  }

  @Test
  void sessaoDeDocumentoSemDuvidaRecebe409() throws Exception {
    ContaDeTeste conta = aguardando();

    iniciar(conta, CORPO_DOCUMENTO)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_FORA_DE_ORDEM"));

    assertThat(contar("select count(*) from tentativa_verificacao")).isZero();
  }

  @Test
  void sessaoDeDocumentoDepoisDaDuvidaRecebe201() throws Exception {
    ContaDeTeste conta = aguardando();
    provaDeVida.programar(ProvaDeVidaDeTeste.concluida(99.0, new FaixaEstimada(17, 24)));
    concluir(conta, sessao(conta)).andExpect(status().isOk());

    iniciar(conta, CORPO_DOCUMENTO).andExpect(status().isCreated());

    assertThat(contar("select count(*) from tentativa_verificacao where finalidade = 'DOCUMENTO'"))
        .isOne();
  }

  @Test
  void aprovadaAtivaAConta() throws Exception {
    ContaDeTeste conta = aguardando();

    concluir(conta, sessao(conta))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"resultado\":\"APROVADA\",\"pedeDocumento\":false,"
                        + "\"tentativasRestantesHoje\":2,\"enviosDeDocumentoRestantesHoje\":2}",
                    JsonCompareMode.STRICT));

    assertThat(estadoDe(conta)).isEqualTo("ATIVA");
    assertThat(
            jdbc.sql("select metodo from verificacao_idade where conta_id = :conta")
                .param("conta", conta.id().valor())
                .query(String.class)
                .list())
        .containsExactly("ESTIMATIVA_FACIAL");
    mvc.perform(get("/api/conta").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.verificada").value(true));
  }

  @Test
  void recusadaSegueOFluxoDoMenor() throws Exception {
    ContaDeTeste conta = aguardando();
    provaDeVida.programar(ProvaDeVidaDeTeste.concluida(99.0, new FaixaEstimada(12, 17)));
    String sessaoId = sessao(conta);

    concluir(conta, sessaoId)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MENOR_DE_IDADE"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    assertThat(estadoDe(conta)).isEqualTo("EXCLUIDA");
    assertThat(contar("select count(*) from email_bloqueado where motivo = 'MENOR'")).isOne();
    assertThat(contar("select count(*) from sessao")).isZero();
    assertThat(contar("select count(*) from tentativa_verificacao")).isZero();
    assertThat(contar("select count(*) from verificacao_idade")).isZero();
  }

  @Test
  void duvidaPedeDocumento() throws Exception {
    ContaDeTeste conta = aguardando();
    provaDeVida.programar(ProvaDeVidaDeTeste.concluida(99.0, new FaixaEstimada(17, 24)));

    concluir(conta, sessao(conta))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("DUVIDA"))
        .andExpect(jsonPath("$.pedeDocumento").value(true));

    situacao(conta).andExpect(jsonPath("$.estado").value("DUVIDA"));
    assertThat(estadoDe(conta)).isEqualTo("AGUARDANDO_VERIFICACAO");
  }

  @Test
  void confiancaBaixaPedeRepetir() throws Exception {
    ContaDeTeste conta = aguardando();
    provaDeVida.programar(ProvaDeVidaDeTeste.concluida(89.9, new FaixaEstimada(30, 40)));

    concluir(conta, sessao(conta))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("REPETIR"))
        .andExpect(jsonPath("$.pedeDocumento").value(false))
        .andExpect(jsonPath("$.tentativasRestantesHoje").value(2));
  }

  @Test
  void resultadoRepetidoDevolveAMesmaResposta() throws Exception {
    ContaDeTeste conta = aguardando();
    String sessaoId = sessao(conta);
    String primeira =
        concluir(conta, sessaoId)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    concluir(conta, sessaoId)
        .andExpect(status().isOk())
        .andExpect(content().json(primeira, JsonCompareMode.STRICT));

    assertThat(provaDeVida.chamadasDeResultado()).isOne();
  }

  @Test
  void resultadosSimultaneosDecidemUmaVez() throws Exception {
    ContaDeTeste conta = aguardando();
    String sessaoId = sessao(conta);
    provaDeVida.segurar();

    try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
      Future<MockHttpServletResponse> primeira =
          threads.submit(() -> concluir(conta, sessaoId).andReturn().getResponse());
      assertThat(provaDeVida.aguardarChamada(ESPERA)).isTrue();

      concluir(conta, sessaoId)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.codigo").value("REQUISICAO_EM_ANDAMENTO"))
          .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));

      provaDeVida.liberar();
      assertThat(primeira.get(ESPERA.toSeconds(), TimeUnit.SECONDS).getStatus()).isEqualTo(200);
    }
    assertThat(provaDeVida.chamadasDeResultado()).isOne();
  }

  @Test
  void sessaoDeOutraContaRecebe404() throws Exception {
    String sessaoId = sessao(aguardando());

    concluir(aguardando(), sessaoId)
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));

    assertThat(provaDeVida.chamadasDeResultado()).isZero();
  }

  @Test
  void sessaoInexistenteRecebe404() throws Exception {
    concluir(aguardando(), "teste-sessao-que-nao-existe")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("SESSAO_DE_VERIFICACAO_INVALIDA"));
  }

  @Test
  void awsForaDoArNoResultadoRecebe503() throws Exception {
    ContaDeTeste conta = aguardando();
    String sessaoId = sessao(conta);
    provaDeVida.falharNoResultado();

    concluir(conta, sessaoId)
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.codigo").value("PROVA_DE_VIDA_INDISPONIVEL"));
    assertThat(estadoDe(conta)).isEqualTo("AGUARDANDO_VERIFICACAO");

    provaDeVida.zerar();
    concluir(conta, sessaoId)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultado").value("APROVADA"));
  }

  @Test
  void situacaoInicial() throws Exception {
    situacao(aguardando())
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(
            content()
                .json(
                    "{\"estado\":\"AGUARDANDO\",\"tentativasRestantesHoje\":3,"
                        + "\"enviosDeDocumentoRestantesHoje\":2,\"modo\":\"falso\"}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void situacaoDaContaAtiva() throws Exception {
    situacao(contas.criarAtiva())
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"estado\":\"APROVADA\",\"tentativasRestantesHoje\":3,"
                        + "\"enviosDeDocumentoRestantesHoje\":2,\"modo\":\"falso\"}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void situacaoDaContaPendenteRecebe409() throws Exception {
    situacao(contas.criar(EstadoConta.PENDENTE, null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_FORA_DE_ORDEM"));
  }

  @Test
  void semSessaoRecebe401() throws Exception {
    mvc.perform(get("/api/conta/verificacao"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void vigesimaPrimeiraChamadaEm10MinRecebe429() throws Exception {
    ContaDeTeste conta = aguardando();
    for (int i = 0; i < 20; i++) {
      concluir(conta, "teste-sessao-que-nao-existe").andExpect(status().isNotFound());
    }

    concluir(conta, "teste-sessao-que-nao-existe")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"));
  }

  @Test
  void fornecedorNuncaRodaEmTransacao() throws Exception {
    ContaDeTeste conta = aguardando();

    concluir(conta, sessao(conta)).andExpect(status().isOk());

    assertThat(provaDeVida.transacaoAtivaNasChamadas()).hasSize(3).containsOnly(false);
  }

  @Test
  @ExtendWith(OutputCaptureExtension.class)
  void logNaoTrazCredencialNemImagem(CapturedOutput saida) throws Exception {
    ContaDeTeste conta = aguardando();

    concluir(conta, sessao(conta)).andExpect(status().isOk());

    assertThat(saida.getOut()).contains(conta.id().toString());
    assertThat(saida.getOut() + saida.getErr())
        .doesNotContain(ProvaDeVidaDeTeste.SEGREDO, ProvaDeVidaDeTeste.CONTEUDO_DA_REFERENCIA);
  }

  private ContaDeTeste aguardando() {
    return contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO);
  }

  /** Abre uma sessão {@code SELFIE} e devolve o id dela. */
  private String sessao(ContaDeTeste conta) throws Exception {
    String corpo =
        iniciar(conta, CORPO_SELFIE)
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return JsonPath.read(corpo, "$.sessaoId");
  }

  private ResultActions iniciar(ContaDeTeste conta, String corpo) throws Exception {
    return mvc.perform(
        post("/api/conta/verificacao/sessao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(cookieDe(conta)));
  }

  private ResultActions concluir(ContaDeTeste conta, String sessaoId) throws Exception {
    return mvc.perform(
        post("/api/conta/verificacao/sessao/{sessaoId}/resultado", sessaoId)
            .header(MesmaOrigem.CABECALHO, "1")
            .cookie(cookieDe(conta)));
  }

  private ResultActions situacao(ContaDeTeste conta) throws Exception {
    return mvc.perform(get("/api/conta/verificacao").cookie(cookieDe(conta)));
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

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
