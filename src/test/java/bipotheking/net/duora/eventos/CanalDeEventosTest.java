package bipotheking.net.duora.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.eventos.dominio.EventoGravado;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

@Import(EntregaEspia.class)
class CanalDeEventosTest extends BancoDeTeste {

  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private CanalDeEventos canal;
  @Autowired private RepositorioDeEventos repositorio;
  @Autowired private EntregaEspia espia;
  @Autowired private ContasDeTeste contas;
  @Autowired private TransactionTemplate transacao;

  private final ExecutorService executor = Executors.newFixedThreadPool(2);
  private ContaId a;
  private ContaId b;

  @BeforeEach
  void preparar() {
    espia.esquecer();
    a = contas.criarAtiva().id();
    b = contas.criarAtiva().id();
  }

  @AfterEach
  void encerrarExecutor() {
    executor.shutdownNow();
  }

  @Test
  void publicarGravaComSeqCrescente() {
    transacao.executeWithoutResult(status ->
        canal.publicar(
            List.of(new Envio(a, evento("fila.estado")), new Envio(a, evento("fila.pareado")))));

    List<EventoGravado> gravados = repositorio.depoisDe(a, 0, 10);

    assertThat(gravados)
        .extracting(EventoGravado::nome)
        .containsExactly("fila.estado", "fila.pareado");
    assertThat(gravados.get(1).seq()).isGreaterThan(gravados.get(0).seq());
  }

  @Test
  void semTransacaoLanca() {
    Evento evento = evento("fila.estado");

    assertThatThrownBy(() -> canal.publicar(a, evento))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void rollbackNaoGravaNemAcorda() {
    transacao.executeWithoutResult(status -> {
      canal.publicar(a, evento("fila.estado"));
      status.setRollbackOnly();
    });

    assertThat(repositorio.depoisDe(a, 0, 10)).isEmpty();
    assertThat(espia.acordadas()).isEmpty();
  }

  @Test
  void acordaSoDepoisDoCommit() {
    List<ContaId> antesDoCommit =
        transacao.execute(status -> {
          canal.publicar(a, evento("fila.estado"));
          return espia.acordadas();
        });

    assertThat(antesDoCommit).isEmpty();
    assertThat(espia.acordadas()).containsExactly(a);
  }

  @Test
  void acordaCadaContaUmaVez() {
    transacao.executeWithoutResult(status ->
        canal.publicar(
            List.of(
                new Envio(a, evento("fila.estado")),
                new Envio(b, evento("fila.estado")),
                new Envio(a, evento("fila.pareado")))));

    assertThat(espia.acordadas()).containsExactlyInAnyOrder(a, b);
  }

  @Test
  void ordemPorContaSegueOCommit() throws Exception {
    CountDownLatch gravouT1 = new CountDownLatch(1);
    CountDownLatch liberaT1 = new CountDownLatch(1);
    CompletableFuture<Long> t1 =
        CompletableFuture.supplyAsync(
            () ->
                transacao.execute(status -> {
                  canal.publicar(a, evento("fila.estado"));
                  gravouT1.countDown();
                  aguardar(liberaT1);
                  return ultimoSeq(a);
                }),
            executor);
    assertThat(gravouT1.await(ESPERA.toSeconds(), TimeUnit.SECONDS)).isTrue();

    CompletableFuture<Void> t2 =
        CompletableFuture.runAsync(
            () ->
                transacao.executeWithoutResult(status -> canal.publicar(a, evento("fila.pareado"))),
            executor);
    aguardarTravaEmEspera();
    assertThat(t2).isNotDone();
    liberaT1.countDown();

    long seqT1 = Objects.requireNonNull(t1.get(ESPERA.toSeconds(), TimeUnit.SECONDS));
    t2.get(ESPERA.toSeconds(), TimeUnit.SECONDS);
    List<EventoGravado> depoisDeT1 = repositorio.depoisDe(a, seqT1, 10);
    assertThat(depoisDeT1).extracting(EventoGravado::nome).containsExactly("fila.pareado");
    assertThat(depoisDeT1.get(0).seq()).isGreaterThan(seqT1);
  }

  @Test
  void duasContasEmOrdemInversaNaoTravam() throws Exception {
    for (int rodada = 0; rodada < 10; rodada++) {
      CyclicBarrier largada = new CyclicBarrier(2);
      CompletableFuture<Void> t1 = publicarApos(largada, List.of(a, b));
      CompletableFuture<Void> t2 = publicarApos(largada, List.of(b, a));

      CompletableFuture.allOf(t1, t2).get(ESPERA.toSeconds(), TimeUnit.SECONDS);
    }

    assertThat(repositorio.depoisDe(a, 0, 100)).hasSize(20);
    assertThat(repositorio.depoisDe(b, 0, 100)).hasSize(20);
  }

  @Test
  void efemeroNaoGrava() {
    Evento evento = evento("fila.estado");

    canal.publicarEfemero(a, evento);

    assertThat(repositorio.depoisDe(a, 0, 10)).isEmpty();
    assertThat(espia.efemeros()).containsExactly(new Envio(a, evento));
  }

  private CompletableFuture<Void> publicarApos(CyclicBarrier largada, List<ContaId> ordem) {
    return CompletableFuture.runAsync(
        () -> {
          aguardar(largada);
          transacao.executeWithoutResult(status ->
              canal.publicar(
                  ordem.stream().map(conta -> new Envio(conta, evento("fila.estado"))).toList()));
        },
        executor);
  }

  private long ultimoSeq(ContaId conta) {
    return jdbc.sql("select max(seq) from evento_conta where conta_id = :c")
        .param("c", conta.valor())
        .query(Long.class)
        .single();
  }

  /** Espera alguma transação ficar parada numa trava consultiva (a T2 na trava da conta). */
  private void aguardarTravaEmEspera() throws InterruptedException {
    Instant limite = Instant.now().plus(ESPERA);
    while (Instant.now().isBefore(limite)) {
      long esperando =
          jdbc.sql("select count(*) from pg_locks where locktype = 'advisory' and not granted")
              .query(Long.class)
              .single();
      if (esperando > 0) {
        return;
      }
      TimeUnit.MILLISECONDS.sleep(20);
    }
    throw new AssertionError("Nenhuma transação esperou a trava da conta");
  }

  private static void aguardar(CountDownLatch trinco) {
    try {
      if (!trinco.await(ESPERA.toSeconds(), TimeUnit.SECONDS)) {
        throw new IllegalStateException("Tempo esgotado esperando o trinco");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }

  private static void aguardar(CyclicBarrier largada) {
    try {
      largada.await(ESPERA.toSeconds(), TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (BrokenBarrierException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }

  private static Evento evento(String nome) {
    return new Evento(nome, Map.of("x", 1));
  }
}
