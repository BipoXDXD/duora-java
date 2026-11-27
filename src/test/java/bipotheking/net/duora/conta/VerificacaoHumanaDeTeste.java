package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dublê da verificação humana nos testes Spring, sem rede: aceita todo token menos {@code falha}
 * (inclusive o {@code XXXX.DUMMY.TOKEN.XXXX} do widget de teste do front). Token nulo, em branco ou
 * com mais de 2048 caracteres é recusado, como no real.
 */
@Component
@Primary
public class VerificacaoHumanaDeTeste implements VerificacaoHumana {

  public static final String TOKEN_RECUSADO = "falha";

  /**
   * As mesmas pré-checagens do {@code TurnstileVerificacao}, para não aceitar o que o real recusa.
   */
  private static final int TAMANHO_MAXIMO_DO_TOKEN = 2048;

  /** Teto da retenção: um teste que esquece de liberar não trava a suíte. */
  private static final Duration RETENCAO_MAXIMA = Duration.ofSeconds(10);

  private final AtomicInteger chamadas = new AtomicInteger();
  private final AtomicReference<Duration> atraso = new AtomicReference<>(Duration.ZERO);
  private final AtomicReference<@Nullable Retencao> retencao = new AtomicReference<>();

  @Override
  public boolean verificar(@Nullable String token, @Nullable String ip) {
    chamadas.incrementAndGet();
    reter();
    esperar();
    if (token == null || token.isBlank() || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
      return false;
    }
    return !TOKEN_RECUSADO.equals(token);
  }

  /** Quantas verificações chegaram desde o último {@link #zerar()}. */
  public int chamadas() {
    return chamadas.get();
  }

  /** Faz cada verificação seguinte demorar {@code duracao}, como um Turnstile lento. */
  public void atrasar(Duration duracao) {
    atraso.set(duracao);
  }

  /**
   * Faz as verificações seguintes pararem dentro do dublê até {@link #liberar()}: garante que duas
   * requisições se sobrepõem sem depender de tempo.
   */
  public void segurar() {
    retencao.set(new Retencao(new CountDownLatch(1), new CountDownLatch(1)));
  }

  /** Espera uma verificação chegar ao dublê depois de {@link #segurar()}. */
  public boolean aguardarChamada(Duration limite) throws InterruptedException {
    Retencao atual = retencao.get();
    return atual != null && atual.chegou().await(limite.toMillis(), TimeUnit.MILLISECONDS);
  }

  /** Solta as verificações seguradas por {@link #segurar()}. */
  public void liberar() {
    Retencao atual = retencao.get();
    if (atual != null) {
      atual.liberacao().countDown();
    }
  }

  /** Volta ao estado inicial: sem chamadas contadas, sem atraso e sem retenção. */
  public void zerar() {
    liberar();
    retencao.set(null);
    chamadas.set(0);
    atraso.set(Duration.ZERO);
  }

  private void reter() {
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

  private void esperar() {
    Duration duracao = atraso.get();
    if (duracao.isZero()) {
      return;
    }
    try {
      Thread.sleep(duracao);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** Avisa que uma verificação chegou e a segura até a liberação. */
  private record Retencao(CountDownLatch chegou, CountDownLatch liberacao) {}
}
