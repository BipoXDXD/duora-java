package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.infra.DespachanteDaOutbox;
import bipotheking.net.duora.notificacao.infra.EmailNoLog;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class PedidoDeEntradaApiTest extends BancoDeTeste {

  private static final String MENSAGEM =
      "Enviamos um link para o seu e-mail. Ele vale por 15 minutos.";
  private static final String TOKEN_BOM = "token-do-widget";
  private static final String TRECHO_DO_LINK = "/entrar/confirmar#t=";
  private static final String CHAVE_DE_IDEMPOTENCIA = "Idempotency-Key";
  private static final String DOMINIO_DESCARTAVEL = "0-mail.com";
  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private VerificacaoHumanaDeTeste verificacaoHumana;
  @Autowired private EmailNoLog emailNoLog;
  @Autowired private DespachanteDaOutbox despachante;
  @Autowired private RepositorioDeBloqueios bloqueios;
  @Autowired private ResumoDeEmail resumoDeEmail;
  @Autowired private ContasDeTeste contas;
  @Autowired private PedidoDeEntrada pedidoDeEntrada;
  @Autowired private PlatformTransactionManager transacoes;
  @Autowired private RandomGenerator aleatorio;

  @BeforeEach
  void zerarVerificacaoHumana() {
    verificacaoHumana.zerar();
  }

  @Test
  void entrarEnviaLinkDeQuinzeMinutos() throws Exception {
    String email = emailNovo();

    entrar(email)
        .andExpect(status().isAccepted())
        .andExpect(content().json("{\"mensagem\":\"" + MENSAGEM + "\"}", JsonCompareMode.STRICT));
    despachante.despacharAgora();

    assertThat(emailNoLog.ultimaPara(email))
        .get()
        .extracting(MensagemDeEmail::texto)
        .asString()
        .contains(TRECHO_DO_LINK);
    assertThat(
            jdbc.sql("select expira_em from link_de_entrada where email = :email")
                .param("email", email)
                .query(OffsetDateTime.class)
                .single()
                .toInstant())
        .isEqualTo(INICIO.plus(Duration.ofMinutes(15)));
  }

  @Test
  void linkEEmailNaMesmaTransacao() {
    Email email = Email.de(emailNovo());
    TransactionTemplate transacao = new TransactionTemplate(transacoes);

    assertThatThrownBy(() ->
            transacao.executeWithoutResult(status -> {
              pedidoDeEntrada.gravarLinkEMensagem(email, Token.novo(aleatorio));
              throw new IllegalStateException("falha forçada depois da outbox");
            }))
        .hasMessage("falha forçada depois da outbox");

    assertThat(links()).isZero();
    assertThat(mensagens()).isZero();
  }

  @Test
  void respostaIgualComOuSemConta() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    String emailDaConta = conta.id().valor() + "@teste.duora.invalid";

    MockHttpServletResponse comConta = entrar(emailDaConta).andReturn().getResponse();
    MockHttpServletResponse semConta = entrar(emailNovo()).andReturn().getResponse();

    assertThat(comConta.getStatus()).isEqualTo(semConta.getStatus()).isEqualTo(202);
    assertThat(comConta.getContentAsString()).isEqualTo(semConta.getContentAsString());
  }

  @Test
  void respostaIgualComEmailBloqueado() throws Exception {
    String bloqueado = emailNovo();
    bloquear(bloqueado);

    MockHttpServletResponse comBloqueio = entrar(bloqueado).andReturn().getResponse();
    MockHttpServletResponse semBloqueio = entrar(emailNovo()).andReturn().getResponse();

    assertThat(comBloqueio.getStatus()).isEqualTo(semBloqueio.getStatus()).isEqualTo(202);
    assertThat(comBloqueio.getContentAsString()).isEqualTo(semBloqueio.getContentAsString());
  }

  @Test
  void emailDescartavelRecusado() throws Exception {
    entrar("alguem@" + DOMINIO_DESCARTAVEL)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EMAIL_DESCARTAVEL"))
        .andExpect(jsonPath("$.mensagem").value("Use um e-mail permanente."));

    assertThat(mensagens()).isZero();
  }

  @Test
  void emailInvalidoRecusado() throws Exception {
    entrar("sem-arroba")
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("EMAIL_INVALIDO"));

    assertThat(mensagens()).isZero();
  }

  @Test
  void verificacaoHumanaRecusada() throws Exception {
    entrar(emailNovo(), VerificacaoHumanaDeTeste.TOKEN_RECUSADO, null, OrigemDeTeste.ipAleatorio())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value("VERIFICACAO_HUMANA_FALHOU"))
        .andExpect(
            jsonPath("$.mensagem")
                .value("Não foi possível confirmar que você é uma pessoa. Tente de novo."));

    assertThat(mensagens()).isZero();
  }

  @Test
  void repeticaoComMesmaChaveNaoDuplicaNemGastaTurnstile() throws Exception {
    String email = emailNovo();
    String chave = chaveNova();

    String primeira = entrarComChave(email, chave).andReturn().getResponse().getContentAsString();
    entrarComChave(email, chave)
        .andExpect(status().isAccepted())
        .andExpect(content().json(primeira, JsonCompareMode.STRICT));

    assertThat(links()).isOne();
    assertThat(mensagens()).isOne();
    assertThat(verificacaoHumana.chamadas()).isOne();
  }

  /**
   * O dublê segura o primeiro toque dentro do Turnstile; o segundo chega com a reserva ainda
   * aberta.
   */
  @Test
  void duploToqueSimultaneoUmTurnstile() throws Exception {
    String email = emailNovo();
    String chave = chaveNova();
    verificacaoHumana.segurar();

    MockHttpServletResponse primeiro;
    try (ExecutorService threads = Executors.newSingleThreadExecutor()) {
      Future<MockHttpServletResponse> primeiroToque =
          threads.submit(() -> entrarComChave(email, chave).andReturn().getResponse());
      try {
        assertThat(verificacaoHumana.aguardarChamada(ESPERA)).isTrue();
        entrarComChave(email, chave)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.codigo").value("REQUISICAO_EM_ANDAMENTO"))
            .andExpect(header().string(HttpHeaders.RETRY_AFTER, "1"));
      } finally {
        verificacaoHumana.liberar();
      }
      primeiro = primeiroToque.get();
    }

    assertThat(primeiro.getStatus()).isEqualTo(202);
    assertThat(primeiro.getContentAsString()).contains(MENSAGEM);
    assertThat(verificacaoHumana.chamadas()).isOne();
    assertThat(links()).isOne();
    assertThat(mensagens()).isOne();
  }

  /**
   * O primeiro pedido grava o link e segura a transação aberta; o segundo, do mesmo e-mail, precisa
   * esperar a trava em vez de gravar um segundo link aberto ao lado (write skew).
   */
  @Test
  void pedidosSimultaneosDoMesmoEmailDeixamUmLinkAberto() throws Exception {
    Email email = Email.de(emailNovo());
    TransactionTemplate transacao = new TransactionTemplate(transacoes);
    CountDownLatch primeiroGravou = new CountDownLatch(1);
    CountDownLatch soltarPrimeiro = new CountDownLatch(1);

    boolean segundoEsperou;
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      Future<?> primeiro =
          threads.submit(() ->
              transacao.executeWithoutResult(status -> {
                pedidoDeEntrada.gravarLinkEMensagem(email, Token.novo(aleatorio));
                primeiroGravou.countDown();
                aguardar(soltarPrimeiro);
              }));
      Future<?> segundo;
      try {
        assertThat(primeiroGravou.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        segundo =
            threads.submit(() ->
                transacao.executeWithoutResult(
                    status -> pedidoDeEntrada.gravarLinkEMensagem(email, Token.novo(aleatorio))));
        segundoEsperou = travaEmEspera();
      } finally {
        soltarPrimeiro.countDown();
      }
      primeiro.get();
      segundo.get();
    }

    assertThat(segundoEsperou).as("o segundo pedido esperou a trava do e-mail").isTrue();
    assertThat(
            jdbc.sql(
                    "select count(*) from link_de_entrada where email = :email and usado_em is null")
                .param("email", email.valor())
                .query(Long.class)
                .single())
        .isOne();
  }

  /**
   * A falha vem no commit, depois do {@code concluir}: a resposta gravada volta atrás com o efeito,
   * a chave é liberada e a mesma chave funciona na tentativa seguinte.
   */
  @Test
  void falhaNaTransacaoDesfazAConclusaoELiberaAChave() throws Exception {
    String email = emailNovo();
    String chave = chaveNova();

    falharNoCommitDaOutboxPara(email);
    try {
      assertThatThrownBy(
              () -> pedidoDeEntrada.pedir(email, TOKEN_BOM, "10.9.9.9", Optional.of(chave)))
          .isInstanceOf(RuntimeException.class);
    } finally {
      removerFalhaNoCommit();
    }

    assertThat(links()).isZero();
    assertThat(mensagens()).isZero();
    assertThat(jdbc.sql("select count(*) from chave_de_idempotencia").query(Long.class).single())
        .isZero();
    entrarComChave(email, chave)
        .andExpect(status().isAccepted())
        .andExpect(content().json("{\"mensagem\":\"" + MENSAGEM + "\"}", JsonCompareMode.STRICT));
    assertThat(links()).isOne();
  }

  @Test
  void mesmaChaveOutroEmailRecusada() throws Exception {
    String chave = chaveNova();
    entrarComChave(emailNovo(), chave).andExpect(status().isAccepted());

    entrarComChave(emailNovo(), chave)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CHAVE_DE_IDEMPOTENCIA_REUSADA"));
  }

  @Test
  void turnstileRecusadoLiberaAChave() throws Exception {
    String email = emailNovo();
    String chave = chaveNova();

    entrar(email, VerificacaoHumanaDeTeste.TOKEN_RECUSADO, chave, OrigemDeTeste.ipAleatorio())
        .andExpect(status().isUnprocessableContent());
    entrar(email, TOKEN_BOM, chave, OrigemDeTeste.ipAleatorio()).andExpect(status().isAccepted());

    assertThat(links()).isOne();
  }

  @Test
  void rajadaPorIpBarrada() throws Exception {
    RequestPostProcessor mesmoIp = OrigemDeTeste.ip("10.20.30.40");
    for (int pedido = 1; pedido <= 20; pedido++) {
      entrar(emailNovo(), TOKEN_BOM, null, mesmoIp).andExpect(status().isAccepted());
    }

    entrar(emailNovo(), TOKEN_BOM, null, mesmoIp)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void quartoPedidoDoMesmoEmailBarradoMesmoTrocandoIp() throws Exception {
    String local = "pessoa-" + UUID.randomUUID();
    for (int pedido = 1; pedido <= 3; pedido++) {
      entrar(local + "@exemplo.com").andExpect(status().isAccepted());
    }

    entrar(local + "+outra@exemplo.com")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void pedidoNovoInvalidaLinkAnterior() throws Exception {
    String email = emailNovo();
    entrar(email).andExpect(status().isAccepted());
    relogio.avancar(Duration.ofMinutes(1));

    entrar(email).andExpect(status().isAccepted());

    List<@Nullable OffsetDateTime> usados =
        jdbc.sql("select usado_em from link_de_entrada where email = :email order by criado_em")
            .param("email", email)
            .query((rs, linha) -> rs.getObject("usado_em", OffsetDateTime.class))
            .list();
    assertThat(usados).hasSize(2);
    assertThat(usados.get(0)).isNotNull();
    assertThat(usados.get(1)).isNull();
  }

  @Test
  void emailBloqueadoRecebeAvisoSemLink() throws Exception {
    String email = emailNovo();
    bloquear(email);

    entrar(email).andExpect(status().isAccepted());
    despachante.despacharAgora();

    MensagemDeEmail aviso = emailNoLog.ultimaPara(email).orElseThrow();
    assertThat(aviso.assunto()).isEqualTo("Cadastro no Duora");
    assertThat(aviso.texto()).doesNotContain("#t=");
    assertThat(aviso.html()).doesNotContain("#t=");
    assertThat(links()).isZero();
  }

  @Test
  void logNaoTrazEmailNemToken(CapturedOutput saida) throws Exception {
    String canario = "canario-" + UUID.randomUUID() + "@exemplo.com";

    entrar(canario).andExpect(status().isAccepted());
    despachante.despacharAgora();

    String texto = emailNoLog.ultimaPara(canario).orElseThrow().texto();
    String aposOLink = texto.substring(texto.indexOf(TRECHO_DO_LINK) + TRECHO_DO_LINK.length());
    String token = aposOLink.substring(0, aposOLink.indexOf('\n'));
    assertThat(saida.getAll()).doesNotContain(canario);
    List<String> registros = registros(saida.getAll());
    assertThat(registros).as("o EmailNoLog escreveu o link").anyMatch(r -> r.contains(token));
    for (String registro : registros) {
      if (registro.contains(token)) {
        assertThat(registro).as("só o EmailNoLog escreve o token").contains("EmailNoLog");
      }
    }
  }

  @Test
  void configuracaoDevolveChaveDoSite() throws Exception {
    mvc.perform(get("/api/conta/configuracao"))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"chaveDoSiteTurnstile\":\"1x00000000000000000000AA\"}",
                    JsonCompareMode.STRICT));
  }

  private ResultActions entrar(String email) throws Exception {
    return entrar(email, TOKEN_BOM, null, OrigemDeTeste.ipAleatorio());
  }

  private ResultActions entrarComChave(String email, String chave) throws Exception {
    return entrar(email, TOKEN_BOM, chave, OrigemDeTeste.ipAleatorio());
  }

  private ResultActions entrar(
      String email, String tokenVerificacao, @Nullable String chave, RequestPostProcessor ip)
      throws Exception {
    MockHttpServletRequestBuilder requisicao =
        post("/api/conta/entrar")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"email\":\"" + email + "\",\"tokenVerificacao\":\"" + tokenVerificacao + "\"}")
            .with(ip);
    if (chave != null) {
      requisicao.header(CHAVE_DE_IDEMPOTENCIA, chave);
    }
    return mvc.perform(requisicao);
  }

  /**
   * Gatilho adiado: o INSERT na outbox passa e a exceção sobe no commit, depois de todo o resto da
   * transação. Só para o e-mail do teste.
   */
  private void falharNoCommitDaOutboxPara(String email) {
    jdbc.sql(
            """
            create or replace function teste_falhar_no_commit() returns trigger
            language plpgsql as $$
            begin
              if new.carga->>'para' = '%s' then
                raise exception 'falha forçada no commit';
              end if;
              return null;
            end $$
            """
                .formatted(email))
        .update();
    jdbc.sql(
            """
            create constraint trigger teste_falhar_no_commit after insert on outbox
            deferrable initially deferred for each row execute function teste_falhar_no_commit()
            """)
        .update();
  }

  private void removerFalhaNoCommit() {
    jdbc.sql("drop trigger if exists teste_falhar_no_commit on outbox").update();
    jdbc.sql("drop function if exists teste_falhar_no_commit()").update();
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

  private void bloquear(String email) {
    bloqueios.bloquear(resumoDeEmail.de(Email.de(email)), "MENOR", INICIO);
  }

  private long links() {
    return jdbc.sql("select count(*) from link_de_entrada").query(Long.class).single();
  }

  private long mensagens() {
    return jdbc.sql("select count(*) from outbox").query(Long.class).single();
  }

  private static String emailNovo() {
    return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
  }

  private static String chaveNova() {
    return UUID.randomUUID().toString();
  }

  /**
   * A saída quebrada em registros de log: cada registro começa numa linha com data e hora; as
   * linhas seguintes sem data (o texto do e-mail, por exemplo) pertencem a ele.
   */
  private static List<String> registros(String saida) {
    List<String> registros = new ArrayList<>();
    StringBuilder atual = new StringBuilder();
    for (String linha : saida.split("\\R")) {
      if (linha.matches("^\\d{4}-\\d{2}-\\d{2}T.*") && !atual.isEmpty()) {
        registros.add(atual.toString());
        atual.setLength(0);
      }
      atual.append(linha).append('\n');
    }
    if (!atual.isEmpty()) {
      registros.add(atual.toString());
    }
    return registros;
  }
}
