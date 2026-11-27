package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class ConfirmacaoApiTest extends BancoDeTeste {

  private static final String LINK_INVALIDO = "LINK_INVALIDO";
  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private PedidoDeEntrada pedidoDeEntrada;
  @Autowired private RepositorioDeContas repositorio;
  @Autowired private RepositorioDeBloqueios bloqueios;
  @Autowired private ResumoDeEmail resumoDeEmail;
  @Autowired private ContasDeTeste contas;
  @Autowired private Sessoes sessoes;
  @Autowired private RandomGenerator aleatorio;
  @Autowired private PlatformTransactionManager transacoes;

  @Test
  void confirmarCriaContaPendenteEAbreSessao() throws Exception {
    String email = emailNovo();
    String token = pedirLink(email);

    confirmar(token)
        .andExpect(status().isOk())
        .andExpect(content().json("{\"cadastroConcluido\":false}", JsonCompareMode.STRICT))
        .andExpect(
            header()
                .string(
                    HttpHeaders.SET_COOKIE,
                    allOf(
                        startsWith("duora_sessao="),
                        containsString("; HttpOnly"),
                        containsString("; Secure"),
                        containsString("; SameSite=Lax"),
                        containsString("; Path=/;"),
                        containsString("; Max-Age=2592000;"))));

    assertThat(estadoDaConta(email)).isEqualTo("PENDENTE");
    assertThat(sessoesAbertas()).isOne();
  }

  @Test
  void contaAtivaRecebeCadastroConcluidoTrue() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    confirmar(pedirLink(emailDe(conta)))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"cadastroConcluido\":true}", JsonCompareMode.STRICT));
  }

  @Test
  void linkUsadoDuasVezesRecusado() throws Exception {
    String token = pedirLink(emailNovo());
    confirmar(token).andExpect(status().isOk());

    confirmar(token)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO))
        .andExpect(jsonPath("$.mensagem").value("Este link expirou ou já foi usado. Peça um novo."))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void linkValeAte14min59s() throws Exception {
    String token = pedirLink(emailNovo());
    relogio.avancar(Duration.ofMinutes(15).minusSeconds(1));

    confirmar(token).andExpect(status().isOk());
  }

  @Test
  void linkExpiraAos15min() throws Exception {
    String token = pedirLink(emailNovo());
    relogio.avancar(Duration.ofMinutes(15));

    confirmar(token)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO));
  }

  @Test
  void tokenInexistenteRecusado() throws Exception {
    confirmar(Token.novo(aleatorio).valor())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO));

    assertThat(contasCriadas()).isZero();
  }

  @Test
  void linkSobreviveAoPreCarregamento() throws Exception {
    String token = pedirLink(emailNovo());

    mvc.perform(get("/entrar/confirmar"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
    assertThat(
            jdbc.sql("select usado_em from link_de_entrada")
                .query((rs, linha) -> rs.getObject("usado_em", OffsetDateTime.class))
                .list())
        .containsExactly((OffsetDateTime) null);

    confirmar(token).andExpect(status().isOk());
  }

  @Test
  void confirmacoesSimultaneasAbremUmaSessao() throws Exception {
    String token = pedirLink(emailNovo());
    Callable<MockHttpServletResponse> clique = () -> confirmar(token).andReturn().getResponse();

    List<Integer> status = new ArrayList<>();
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      for (Future<MockHttpServletResponse> resposta : threads.invokeAll(List.of(clique, clique))) {
        status.add(resposta.get().getStatus());
      }
    }

    assertThat(status).containsExactlyInAnyOrder(200, 422);
    assertThat(sessoesAbertas()).isOne();
  }

  /**
   * Outra transação criou a conta do mesmo e-mail e ainda não fez commit. A confirmação espera a
   * inserção e, com o commit da outra, abre a sessão na conta que já existe, sem erro 500.
   */
  @Test
  void confirmacaoQueEsperaCriacaoConcorrenteUsaAContaCriada() throws Exception {
    String email = emailNovo();
    String token = pedirLink(email);
    CountDownLatch criou = new CountDownLatch(1);
    CountDownLatch soltar = new CountDownLatch(1);

    boolean esperou;
    int status;
    try (ExecutorService threads = Executors.newFixedThreadPool(2)) {
      Future<?> outra =
          threads.submit(() ->
              new TransactionTemplate(transacoes)
                  .executeWithoutResult(transacao -> {
                    repositorio.criarPendente(Email.de(email));
                    criou.countDown();
                    aguardar(soltar);
                  }));
      Future<MockHttpServletResponse> resposta;
      try {
        assertThat(criou.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        resposta = threads.submit(() -> confirmar(token).andReturn().getResponse());
        esperou = conexoesEmEspera();
      } finally {
        soltar.countDown();
      }
      outra.get();
      status = resposta.get().getStatus();
    }

    assertThat(esperou).as("a confirmação esperou a inserção da outra transação").isTrue();
    assertThat(status).isEqualTo(200);
    assertThat(contasCriadas()).isOne();
    assertThat(sessoesAbertas()).isOne();
  }

  @Test
  void loginNovoEncerraSessaoAnterior() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    Cookie anterior = cookieDe(conta);

    confirmar(pedirLink(emailDe(conta)), anterior).andExpect(status().isOk());

    assertThat(
            jdbc.sql("select count(*) from sessao where token_hash = :resumo")
                .param("resumo", Resumos.sha256(anterior.getValue()))
                .query(Long.class)
                .single())
        .isZero();
    assertThat(sessoesAbertas()).isOne();
  }

  @Test
  void contaSuspensaRecebe403() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.SUSPENSA, NASCIMENTO);
    sessoes.revogarTodas(conta.id());
    String token = pedirLink(emailDe(conta));

    confirmar(token)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CONTA_INDISPONIVEL"))
        .andExpect(jsonPath("$.mensagem").value("Esta conta não está disponível."))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    assertThat(sessoesAbertas()).isZero();
    assertThat(
            jdbc.sql("select count(*) from link_de_entrada where usado_em is null")
                .query(Long.class)
                .single())
        .as("a transação desfaz o consumo do link")
        .isOne();
  }

  @Test
  void tokenVazioRecusado() throws Exception {
    confirmarComCorpo("{\"token\":\"\"}", OrigemDeTeste.ipAleatorio())
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO));
  }

  @Test
  void campoExtraRecebe400() throws Exception {
    String token = pedirLink(emailNovo());

    confirmarComCorpo(
            "{\"token\":\"" + token + "\",\"estado\":\"ATIVA\"}", OrigemDeTeste.ipAleatorio())
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(contasCriadas()).isZero();
  }

  @Test
  void linkAntigoDeEmailBloqueadoNaoCriaConta() throws Exception {
    String email = emailNovo();
    String token = pedirLink(email);
    bloqueios.bloquear(resumoDeEmail.de(Email.de(email)), "MENOR", INICIO);

    confirmar(token)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    assertThat(contasCriadas()).isZero();
    assertThat(sessoesAbertas()).isZero();
  }

  @Test
  void linkAntigoNaoReativaContaPendenteDeEmailBloqueado() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.PENDENTE, null);
    sessoes.revogarTodas(conta.id());
    String email = emailDe(conta);
    String token = pedirLink(email);
    bloqueios.bloquear(resumoDeEmail.de(Email.de(email)), "MENOR", INICIO);

    confirmar(token)
        .andExpect(status().isUnprocessableContent())
        .andExpect(jsonPath("$.codigo").value(LINK_INVALIDO))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));

    assertThat(contasCriadas()).isOne();
    assertThat(estadoDaConta(email)).isEqualTo("PENDENTE");
    assertThat(sessoesAbertas()).isZero();
  }

  @Test
  void rajadaDeConfirmacaoPorIpBarrada() throws Exception {
    RequestPostProcessor mesmoIp = OrigemDeTeste.ip("10.40.30.20");
    for (int tentativa = 1; tentativa <= 30; tentativa++) {
      confirmarComCorpo(corpo(Token.novo(aleatorio).valor()), mesmoIp)
          .andExpect(status().isUnprocessableContent());
    }

    confirmarComCorpo(corpo(Token.novo(aleatorio).valor()), mesmoIp)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  /** Grava o link como o pedido grava (15 min de validade) e devolve o token que iria no e-mail. */
  private String pedirLink(String email) {
    Token token = Token.novo(aleatorio);
    new TransactionTemplate(transacoes)
        .executeWithoutResult(
            status -> pedidoDeEntrada.registrarNaTransacao(Email.de(email), token));
    return token.valor();
  }

  private ResultActions confirmar(String token) throws Exception {
    return confirmar(token, null);
  }

  private ResultActions confirmar(String token, @Nullable Cookie cookie) throws Exception {
    MockHttpServletRequestBuilder requisicao =
        requisicao(corpo(token), OrigemDeTeste.ipAleatorio());
    if (cookie != null) {
      requisicao.cookie(cookie);
    }
    return mvc.perform(requisicao);
  }

  private ResultActions confirmarComCorpo(String corpo, RequestPostProcessor ip) throws Exception {
    return mvc.perform(requisicao(corpo, ip));
  }

  private static MockHttpServletRequestBuilder requisicao(String corpo, RequestPostProcessor ip) {
    return post("/api/conta/confirmar")
        .header(MesmaOrigem.CABECALHO, "1")
        .contentType(MediaType.APPLICATION_JSON)
        .content(corpo)
        .with(ip);
  }

  private static String corpo(String token) {
    return "{\"token\":\"" + token + "\"}";
  }

  private String estadoDaConta(String email) {
    return jdbc.sql("select estado from conta where email = :email")
        .param("email", email)
        .query(String.class)
        .single();
  }

  /** Espera uma conexão presa na trava de outra transação, até o limite. */
  private boolean conexoesEmEspera() throws InterruptedException {
    long limite = System.nanoTime() + ESPERA.toNanos();
    while (System.nanoTime() < limite) {
      long esperando =
          jdbc.sql("select count(*) from pg_locks where locktype = 'transactionid' and not granted")
              .query(Long.class)
              .single();
      if (esperando >= 1) {
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

  private long contasCriadas() {
    return jdbc.sql("select count(*) from conta").query(Long.class).single();
  }

  private long sessoesAbertas() {
    return jdbc.sql("select count(*) from sessao").query(Long.class).single();
  }

  private static String emailDe(ContaDeTeste conta) {
    return conta.id().valor() + "@teste.duora.invalid";
  }

  private static String emailNovo() {
    return "pessoa-" + UUID.randomUUID() + "@exemplo.com";
  }

  /** O {@code ContaDeTeste} traz o cookie como {@code nome=valor}. */
  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
