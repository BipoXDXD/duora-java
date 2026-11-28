package bipotheking.net.duora.conta;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Retenção das chamadas de um dublê: depois de {@link #segurar()}, cada chamada que passa por
 * {@link #reter()} avisa que chegou e para até {@link #liberar()}. Garante que duas requisições se
 * sobrepõem sem depender de tempo.
 */
final class RetencaoDeTeste {

  /** Teto da retenção: um teste que esquece de liberar não trava a suíte. */
  private static final Duration RETENCAO_MAXIMA = Duration.ofSeconds(10);

  private final AtomicReference<@Nullable Retencao> retencao = new AtomicReference<>();

  /** Faz as chamadas seguintes pararem em {@link #reter()} até {@link #liberar()}. */
  void segurar() {
    retencao.set(new Retencao(new CountDownLatch(1), new CountDownLatch(1)));
  }

  /** Espera uma chamada chegar a {@link #reter()} depois de {@link #segurar()}. */
  boolean aguardarChamada(Duration limite) throws InterruptedException {
    Retencao atual = retencao.get();
    return atual != null && atual.chegou().await(limite.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Solta as chamadas seguradas por {@link #segurar()}. */
  void liberar() {
    Retencao atual = retencao.get();
    if (atual != null) {
      atual.liberacao().countDown();
    }
  }

  /** Solta o que estiver segurado e desliga a retenção. */
  void zerar() {
    liberar();
    retencao.set(null);
  }

  /** Sem {@link #segurar()}, não faz nada; com ele, avisa a chegada e espera a liberação. */
  void reter() {
    Retencao atual = retencao.get();
    if (atual == null) {
      return;
    }
    atual.chegou().countDown();
    try {
      atual.liberacao().await(RETENCAO_MAXIMA.toMillis(), TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Avisa que uma chamada chegou e a segura até a liberação. */
  private record Retencao(CountDownLatch chegou, CountDownLatch liberacao) {}
}
