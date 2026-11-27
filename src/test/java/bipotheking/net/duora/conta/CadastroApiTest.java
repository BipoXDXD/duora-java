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
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.infra.DespachanteDaOutbox;
import bipotheking.net.duora.notificacao.infra.EmailNoLog;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class CadastroApiTest extends BancoDeTeste {

  private static final String MAIOR = "1990-05-10";
  private static final String MENOR = "2010-03-15";
  private static final String COOKIE_EXPIRADO = "Max-Age=0";
  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private ContasDeTeste contas;
  @Autowired private DocumentosLegais documentos;
  @Autowired private PedidoDeEntrada pedidoDeEntrada;
  @Autowired private PlatformTransactionManager transacoes;
  @Autowired private RandomGenerator aleatorio;
  @Autowired private DespachanteDaOutbox despachante;
  @Autowired private EmailNoLog emailNoLog;

  @Test
  void maiorVaiParaAguardandoVerificacao() throws Exception {
    ContaDeTeste conta = pendente();

    cadastrar(conta, corpo(MAIOR))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"cadastroConcluido\":false,\"proximoPasso\":\"VERIFICACAO\"}",
                    JsonCompareMode.STRICT));

    mvc.perform(get("/api/conta").cookie(cookieDe(conta)))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"estado\":\"AGUARDANDO_VERIFICACAO\",\"cadastroConcluido\":false,"
                        + "\"verificada\":false}",
                    JsonCompareMode.STRICT));
    assertThat(nascimentoDe(conta)).isEqualTo(LocalDate.parse(MAIOR));
    assertThat(aceitesDe(conta))
        .containsExactlyInAnyOrder(
            aceite(DocumentoLegal.TERMOS, documentos.atual(DocumentoLegal.TERMOS)),
            aceite(DocumentoLegal.PRIVACIDADE, documentos.atual(DocumentoLegal.PRIVACIDADE)));
    mvc.perform(get("/api/teste/so-ativa").cookie(cookieDe(conta)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CADASTRO_INCOMPLETO"));
  }

  @Test
  void segundoEnvioRecusado() throws Exception {
    ContaDeTeste conta = pendente();
    cadastrar(conta, corpo(MAIOR)).andExpect(status().isOk());

    cadastrar(conta, corpo("1985-01-01"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CADASTRO_JA_CONCLUIDO"))
        .andExpect(jsonPath("$.mensagem").value("O cadastro já foi concluído."));

    assertThat(nascimentoDe(conta)).isEqualTo(LocalDate.parse(MAIOR));
  }

  @Test
  void cadastrosSimultaneosUmVence() throws Exception {
    ContaDeTeste conta = pendente();
    Callable<MockHttpServletResponse> envio =
        () -> cadastrar(conta, corpo(MAIOR)).andReturn().getResponse();

    List<Integer> status = new ArrayList<>();
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      for (Future<MockHttpServletResponse> resposta : threads.invokeAll(List.of(envio, envio))) {
        status.add(resposta.get().getStatus());
      }
    }

    assertThat(status).containsExactlyInAnyOrder(200, 409);
    assertThat(aceitesDe(conta)).hasSize(2);
  }

  @Test
  void menorRecusadoEBloqueado() throws Exception {
    ContaDeTeste conta = pendente();
    pedirLink(emailDe(conta));

    cadastrar(conta, corpo(MENOR))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MENOR_DE_IDADE"))
        .andExpect(jsonPath("$.mensagem").value("O Duora é só para maiores de 18 anos."))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(COOKIE_EXPIRADO)));

    Map<String, Object> linha =
        jdbc.sql("select estado, email, data_nascimento, excluida_em from conta where id = :id")
            .param("id", conta.id().valor())
            .query()
            .singleRow();
    assertThat(linha.get("estado")).isEqualTo("EXCLUIDA");
    assertThat(linha.get("email")).isNull();
    assertThat(linha.get("data_nascimento")).isNull();
    assertThat(linha.get("excluida_em")).isNotNull();
    assertThat(contar("select count(*) from email_bloqueado where motivo = 'MENOR'")).isOne();
    assertThat(contar("select count(*) from sessao")).isZero();
    assertThat(contar("select count(*) from aceite_documento")).isZero();
    assertThat(contar("select count(*) from link_de_entrada where usado_em is null"))
        .as("o link aberto do e-mail foi invalidado")
        .isZero();
    Map<String, Object> mensagem =
        jdbc.sql("select carga, ultimo_erro from outbox").query().singleRow();
    assertThat(mensagem.get("carga")).isNull();
    assertThat(mensagem.get("ultimo_erro")).isEqualTo("titular-esquecido");
  }

  /**
   * Um pedido de link do mesmo e-mail, com link e mensagem ainda sem commit, segura a trava do
   * e-mail: a recusa por idade espera por ele e, depois, invalida o link e esquece a mensagem. Sem
   * a trava, a recusa terminaria antes e o link e a carga ficariam vivos.
   */
  @Test
  void recusaPorIdadeEsperaPedidoDeLinkEmAndamento() throws Exception {
    ContaDeTeste conta = pendente();
    Email email = Email.de(emailDe(conta));
    CountDownLatch pedidoGravou = new CountDownLatch(1);
    CountDownLatch soltarPedido = new CountDownLatch(1);

    boolean recusaEsperou;
    int statusDaRecusa;
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      Future<?> pedido =
          threads.submit(() ->
              new TransactionTemplate(transacoes)
                  .executeWithoutResult(status -> {
                    pedidoDeEntrada.gravarLinkEMensagem(email, Token.novo(aleatorio));
                    pedidoGravou.countDown();
                    aguardar(soltarPedido);
                  }));
      Future<MockHttpServletResponse> recusa;
      try {
        assertThat(pedidoGravou.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        recusa = threads.submit(() -> cadastrar(conta, corpo(MENOR)).andReturn().getResponse());
        recusaEsperou = travaEmEspera();
      } finally {
        soltarPedido.countDown();
      }
      pedido.get();
      statusDaRecusa = recusa.get().getStatus();
    }

    assertThat(recusaEsperou).as("a recusa esperou a trava do e-mail").isTrue();
    assertThat(statusDaRecusa).isEqualTo(422);
    assertThat(contar("select count(*) from link_de_entrada where usado_em is null")).isZero();
    assertThat(contar("select count(*) from outbox where carga is not null")).isZero();
  }

  @Test
  void menorNaoReabreContaComLinkAntigo() throws Exception {
    String email = emailNovo();
    Cookie sessao = confirmarLink(pedirLink(email));
    String linkB = pedirLink(email);

    cadastrar(sessao, corpo(MENOR)).andExpect(status().isUnprocessableContent());

    confirmar(linkB)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("LINK_INVALIDO"));
    assertThat(contar("select count(*) from conta")).isOne();
    assertThat(contar("select count(*) from conta where estado = 'EXCLUIDA'")).isOne();
  }

  @Test
  void menorTentaDeNovoComVariacaoDoEmail() throws Exception {
    Cookie sessao = confirmarLink(pedirLink("Nome+x@gmail.com"));
    cadastrar(sessao, corpo(MENOR)).andExpect(status().isUnprocessableContent());
    despachante.despacharAgora();

    mvc.perform(
            post("/api/conta/entrar")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"n.ome@gmail.com\",\"tokenVerificacao\":\"token-do-widget\"}")
                .with(OrigemDeTeste.ipAleatorio()))
        .andExpect(status().isAccepted());
    despachante.despacharAgora();

    MensagemDeEmail aviso = emailNoLog.ultimaPara("n.ome@gmail.com").orElseThrow();
    assertThat(aviso.assunto()).isEqualTo("Cadastro no Duora");
    assertThat(aviso.texto()).doesNotContain("#t=");
    assertThat(contar("select count(*) from link_de_entrada where email = 'n.ome@gmail.com'"))
        .isZero();
  }

  @Test
  void versaoAntigaRecusada() throws Exception {
    ContaDeTeste conta = pendente();

    cadastrar(conta, corpo(MAIOR, "2020-01-01", versao(DocumentoLegal.PRIVACIDADE), true))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("TERMOS_DESATUALIZADOS"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Os termos mudaram. Recarregue a página e leia a versão nova."));

    assertThat(estadoDe(conta)).isEqualTo("PENDENTE");
  }

  @Test
  void semAceiteRecusado() throws Exception {
    ContaDeTeste conta = pendente();

    cadastrar(conta, corpoSemAceite())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("ACEITE_NECESSARIO"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Aceite os Termos de uso e a Política de privacidade para continuar."));

    assertThat(estadoDe(conta)).isEqualTo("PENDENTE");
  }

  @Test
  void semDataDeNascimentoRecusado() throws Exception {
    ContaDeTeste conta = pendente();
    String semData =
        "{\"versaoTermos\":\"%s\",\"versaoPrivacidade\":\"%s\",\"aceito\":true}"
            .formatted(versao(DocumentoLegal.TERMOS), versao(DocumentoLegal.PRIVACIDADE));

    cadastrar(conta, semData)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("DATA_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value("Confira a data de nascimento."));
  }

  @Test
  void campoExtraRecusado() throws Exception {
    ContaDeTeste conta = pendente();
    String comEstado = corpo(MAIOR).replace("}", ",\"estado\":\"ATIVA\"}");

    cadastrar(conta, comEstado)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(estadoDe(conta)).isEqualTo("PENDENTE");
    assertThat(nascimentoDe(conta)).isNull();
  }

  @Test
  void idadeUsaFusoDeSaoPaulo() throws Exception {
    relogio.ajustar(Instant.parse("2026-11-22T02:00:00Z"));
    ContaDeTeste conta = pendente();

    cadastrar(conta, corpo("2008-11-22"))
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("MENOR_DE_IDADE"));
  }

  @Test
  void idadeVira18AMeiaNoiteDeSaoPaulo() throws Exception {
    relogio.ajustar(Instant.parse("2026-11-22T03:00:00Z"));
    ContaDeTeste conta = pendente();

    cadastrar(conta, corpo("2008-11-22")).andExpect(status().isOk());
  }

  @Test
  void undecimoCadastroEm10MinRecebe429() throws Exception {
    ContaDeTeste conta = pendente();
    for (int envio = 1; envio <= 10; envio++) {
      cadastrar(conta, corpoSemAceite()).andExpect(status().isUnprocessableContent());
    }

    cadastrar(conta, corpo(MAIOR))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    assertThat(estadoDe(conta)).isEqualTo("PENDENTE");
  }

  private ContaDeTeste pendente() {
    return contas.criar(EstadoConta.PENDENTE, null);
  }

  private ResultActions cadastrar(ContaDeTeste conta, String corpo) throws Exception {
    return cadastrar(cookieDe(conta), corpo);
  }

  private ResultActions cadastrar(Cookie sessao, String corpo) throws Exception {
    return mvc.perform(
        post("/api/conta/cadastro")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(sessao));
  }

  private String corpo(String nascimento) {
    return corpo(
        nascimento, versao(DocumentoLegal.TERMOS), versao(DocumentoLegal.PRIVACIDADE), true);
  }

  private String corpoSemAceite() {
    return corpo(MAIOR, versao(DocumentoLegal.TERMOS), versao(DocumentoLegal.PRIVACIDADE), false);
  }

  private static String corpo(
      String nascimento, String versaoTermos, String versaoPrivacidade, boolean aceito) {
    return "{\"dataDeNascimento\":\"%s\",\"versaoTermos\":\"%s\",\"versaoPrivacidade\":\"%s\",\"aceito\":%s}"
        .formatted(nascimento, versaoTermos, versaoPrivacidade, aceito);
  }

  private String versao(DocumentoLegal documento) {
    return documentos.atual(documento).versao();
  }

  /** Grava o link como o pedido grava, com a mensagem pendente na outbox, e devolve o token. */
  private String pedirLink(String email) {
    Token token = Token.novo(aleatorio);
    new TransactionTemplate(transacoes)
        .executeWithoutResult(
            status -> pedidoDeEntrada.gravarLinkEMensagem(Email.de(email), token));
    return token.valor();
  }

  private ResultActions confirmar(String token) throws Exception {
    return mvc.perform(
        post("/api/conta/confirmar")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"token\":\"" + token + "\"}")
            .with(OrigemDeTeste.ipAleatorio()));
  }

  /** Confirma o link e devolve o cookie da sessão aberta. */
  private Cookie confirmarLink(String token) throws Exception {
    String setCookie =
        confirmar(token)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader(HttpHeaders.SET_COOKIE);
    assertThat(setCookie).isNotNull();
    String par = setCookie.substring(0, setCookie.indexOf(';'));
    String[] partes = par.split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }

  private String estadoDe(ContaDeTeste conta) {
    return jdbc.sql("select estado from conta where id = :id")
        .param("id", conta.id().valor())
        .query(String.class)
        .single();
  }

  /** A data de nascimento gravada; nula antes do cadastro. */
  private @Nullable LocalDate nascimentoDe(ContaDeTeste conta) {
    return jdbc.sql("select data_nascimento from conta where id = :id")
        .param("id", conta.id().valor())
        .query((rs, linha) -> rs.getObject("data_nascimento", LocalDate.class))
        .list()
        .getFirst();
  }

  private List<Aceite> aceitesDe(ContaDeTeste conta) {
    return jdbc.sql(
            """
            select documento, versao, resumo_sha256, aceito_em
            from aceite_documento where conta_id = :id
            """)
        .param("id", conta.id().valor())
        .query((rs, linha) ->
            new Aceite(
                rs.getString("documento"),
                rs.getString("versao"),
                rs.getString("resumo_sha256"),
                rs.getObject("aceito_em", OffsetDateTime.class).toInstant()))
        .list();
  }

  private static Aceite aceite(DocumentoLegal documento, VersaoDeDocumento versao) {
    return new Aceite(documento.name(), versao.versao(), versao.resumoSha256(), INICIO);
  }

  /** Espera alguma conexão ficar presa numa trava advisory, até o limite. */
  private boolean travaEmEspera() throws InterruptedException {
    long limite = System.nanoTime() + ESPERA.toNanos();
    while (System.nanoTime() < limite) {
      long esperando =
          jdbc.sql("select count(*) from pg_locks where locktype = 'advisory' and not granted")
              .query(Long.class)
              .single();
      if (esperando > 0) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static void aguardar(CountDownLatch trinco) {
    try {
      // Folga maior que a da sondagem da trava, para a falha sair na asserção, não aqui.
      if (!trinco.await(ESPERA.multipliedBy(3).toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("O teste não soltou a transação a tempo");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private long contar(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private static String emailDe(ContaDeTeste conta) {
    return conta.id().valor() + "@teste.duora.invalid";
  }

  private static String emailNovo() {
    return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }

  private record Aceite(String documento, String versao, String resumoSha256, Instant aceitoEm) {}
}
