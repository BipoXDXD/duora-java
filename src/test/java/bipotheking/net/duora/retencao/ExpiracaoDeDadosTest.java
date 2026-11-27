package bipotheking.net.duora.retencao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(OutputCaptureExtension.class)
class ExpiracaoDeDadosTest {

  private static final Instant AGORA = Instant.parse("2026-11-22T06:30:00Z");
  private static final String CANARIO = "canario-da-retencao-9b1c@exemplo.invalid";

  private final Clock relogio = Clock.fixed(AGORA, ZoneOffset.UTC);
  private final GeradorDeIds geradorDeIds =
      new GeradorDeIds(relogio, RandomGenerator.of("L64X128MixRandom"));
  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @AfterEach
  void limparMdc() {
    MDC.remove(Correlacao.CHAVE_MDC);
  }

  private ExpiracaoDeDados expiracao(PoliticaDeRetencao... politicas) {
    return new ExpiracaoDeDados(List.of(politicas), relogio, geradorDeIds, metricas);
  }

  @Test
  void limiteEAgoraMenosOPrazo() {
    PoliticaFalsa politica = new PoliticaFalsa("links", Duration.parse("P30D"));

    expiracao(politica).executar();

    assertThat(politica.limites()).containsExactly(Instant.parse("2026-10-23T06:30:00Z"));
  }

  @Test
  void rodaEmOrdemDeNome() {
    List<String> ordem = Collections.synchronizedList(new ArrayList<>());

    List<ResultadoDaExpiracao> resultados =
        expiracao(
                new PoliticaFalsa("sessoes").anotandoEm(ordem),
                new PoliticaFalsa("chaves-de-idempotencia").anotandoEm(ordem),
                new PoliticaFalsa("links").anotandoEm(ordem))
            .executar();

    assertThat(ordem).containsExactly("chaves-de-idempotencia", "links", "sessoes");
    assertThat(resultados)
        .extracting(ResultadoDaExpiracao::politica)
        .containsExactly("chaves-de-idempotencia", "links", "sessoes");
  }

  @Test
  void falhaDeUmaPoliticaNaoParaAsOutras(CapturedOutput saida) {
    PoliticaFalsa primeira = new PoliticaFalsa("a-primeira").removendo(3);
    PoliticaFalsa doMeio =
        new PoliticaFalsa("b-do-meio").falhandoCom(new IllegalStateException("banco"));
    PoliticaFalsa ultima = new PoliticaFalsa("c-ultima").removendo(5);

    List<ResultadoDaExpiracao> resultados = expiracao(ultima, doMeio, primeira).executar();

    assertThat(resultados)
        .containsExactly(
            new ResultadoDaExpiracao("a-primeira", 3, false),
            new ResultadoDaExpiracao("b-do-meio", 0, true),
            new ResultadoDaExpiracao("c-ultima", 5, false));
    String erro = saida.getOut().lines().filter(l -> l.contains("ERROR")).findFirst().orElseThrow();
    assertThat(erro).contains("b-do-meio");
  }

  @Test
  void nomesRepetidosFalhamNaConstrucao() {
    PoliticaFalsa uma = new PoliticaFalsa("links");
    PoliticaFalsa outra = new PoliticaFalsa("links");

    assertThatThrownBy(() -> expiracao(uma, outra))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("links");
  }

  @Test
  void nomeForaDeKebabCaseFalhaNaConstrucao() {
    PoliticaFalsa politica = new PoliticaFalsa("Links_Magicos");

    assertThatThrownBy(() -> expiracao(politica))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Links_Magicos");
  }

  @Test
  void prazoQueNaoEPositivoFalhaNaConstrucao() {
    PoliticaFalsa politica = new PoliticaFalsa("links", Duration.ofDays(-1));

    assertThatThrownBy(() -> expiracao(politica))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("links");
  }

  @Test
  void execucoesSimultaneasSaoSerializadas() throws Exception {
    CountDownLatch entrou = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    PoliticaFalsa politica = new PoliticaFalsa("links").pausando(entrou, liberar);
    ExpiracaoDeDados expiracao = expiracao(politica);
    ExecutorService threads = Executors.newFixedThreadPool(2);
    try {
      Future<List<ResultadoDaExpiracao>> primeira = threads.submit(expiracao::executar);
      assertThat(entrou.await(5, TimeUnit.SECONDS)).isTrue();
      AtomicReference<Thread> segundaThread = new AtomicReference<>();
      Future<List<ResultadoDaExpiracao>> segunda =
          threads.submit(() -> {
            segundaThread.set(Thread.currentThread());
            return expiracao.executar();
          });
      esperarBloqueada(segundaThread);

      liberar.countDown();

      assertThat(primeira.get(5, TimeUnit.SECONDS)).hasSize(1);
      assertThat(segunda.get(5, TimeUnit.SECONDS)).hasSize(1);
    } finally {
      threads.shutdownNow();
    }
    assertThat(politica.limites()).hasSize(2);
    assertThat(politica.viuConcorrencia()).isFalse();
  }

  /** Espera a segunda thread parar no monitor de {@code executar()}, com a primeira lá dentro. */
  private static void esperarBloqueada(AtomicReference<Thread> thread) throws InterruptedException {
    long ate = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (thread.get() == null || thread.get().getState() != Thread.State.BLOCKED) {
      if (System.nanoTime() > ate) {
        throw new AssertionError("A segunda execução não bloqueou");
      }
      Thread.sleep(5);
    }
  }

  @Test
  void prazosExpostos() {
    ExpiracaoDeDados expiracao =
        expiracao(
            new PoliticaFalsa("sessoes", Duration.ofDays(30)),
            new PoliticaFalsa("links", Duration.ofDays(1)));

    assertThat(expiracao.prazos())
        .containsExactly(
            Map.entry("links", Duration.ofDays(1)), Map.entry("sessoes", Duration.ofDays(30)));
  }

  @Test
  void metricasPorPolitica() {
    expiracao(
            new PoliticaFalsa("a").removendo(7).comTamanho(42),
            new PoliticaFalsa("b").falhandoCom(new IllegalStateException("banco")).comTamanho(3))
        .executar();

    assertThat(metricas.get("duora.retencao.removidos").tag("politica", "a").counter().count())
        .isEqualTo(7.0);
    assertThat(metricas.get("duora.retencao.tamanho").tag("politica", "a").gauge().value())
        .isEqualTo(42.0);
    assertThat(metricas.get("duora.retencao.tamanho").tag("politica", "b").gauge().value())
        .isEqualTo(3.0);
    assertThat(metricas.get("duora.retencao.falhas").tag("politica", "a").counter().count())
        .isZero();
    assertThat(metricas.get("duora.retencao.falhas").tag("politica", "b").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void logSoTemNomeEContagem(CapturedOutput saida) {
    expiracao(
            new PoliticaComCanario("com-canario").removendo(4),
            new PoliticaComCanario("falha-com-canario")
                .falhandoCom(new IllegalStateException("falhou para " + CANARIO)))
        .executar();

    assertThat(saida.getAll()).contains("com-canario").contains("4").doesNotContain(CANARIO);
  }

  @Test
  void cadaExecucaoTemCorrelationIdProprio() {
    PoliticaFalsa politica = new PoliticaFalsa("links");
    ExpiracaoDeDados expiracao = expiracao(politica);

    expiracao.executar();
    expiracao.executar();

    List<String> ids = politica.correlationIds().stream().map(String::valueOf).toList();
    assertThat(ids).hasSize(2).doesNotHaveDuplicates();
    assertThat(ids).allSatisfy(id -> assertThat(UUID.fromString(id).version()).isEqualTo(7));
    assertThat(MDC.get(Correlacao.CHAVE_MDC)).isNull();
  }

  @Test
  void dentroDeTransacaoFalhaRapido() {
    PoliticaFalsa politica = new PoliticaFalsa("links");
    ExpiracaoDeDados expiracao = expiracao(politica);
    TransactionSynchronizationManager.setActualTransactionActive(true);
    try {
      assertThatThrownBy(expiracao::executar).isInstanceOf(IllegalStateException.class);
    } finally {
      TransactionSynchronizationManager.setActualTransactionActive(false);
    }
    assertThat(politica.limites()).isEmpty();
  }

  /** Guarda um e-mail no estado e no {@code toString()}: nada disso pode ir para o log. */
  private static final class PoliticaComCanario implements PoliticaDeRetencao {

    private final PoliticaFalsa delegada;
    private final String titular = CANARIO;

    PoliticaComCanario(String nome) {
      this.delegada = new PoliticaFalsa(nome);
    }

    PoliticaComCanario removendo(int quantos) {
      delegada.removendo(quantos);
      return this;
    }

    PoliticaComCanario falhandoCom(RuntimeException erro) {
      delegada.falhandoCom(erro);
      return this;
    }

    @Override
    public String nome() {
      return delegada.nome();
    }

    @Override
    public Duration prazo() {
      return delegada.prazo();
    }

    @Override
    public int expirar(Instant limite) {
      return delegada.expirar(limite);
    }

    @Override
    public long tamanho() {
      return delegada.tamanho();
    }

    @Override
    public String toString() {
      return "PoliticaComCanario[" + titular + "]";
    }
  }
}
