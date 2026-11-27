package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Dublê da verificação humana nos testes Spring, sem rede: aceita todo token menos {@code falha}
 * (inclusive o {@code XXXX.DUMMY.TOKEN.XXXX} do widget de teste do front).
 */
@Component
@Primary
public class VerificacaoHumanaDeTeste implements VerificacaoHumana {

  public static final String TOKEN_RECUSADO = "falha";

  private final AtomicInteger chamadas = new AtomicInteger();
  private final AtomicReference<Duration> atraso = new AtomicReference<>(Duration.ZERO);

  @Override
  public boolean verificar(String token, String ip) {
    chamadas.incrementAndGet();
    esperar();
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

  /** Volta ao estado inicial: sem chamadas contadas e sem atraso. */
  public void zerar() {
    chamadas.set(0);
    atraso.set(Duration.ZERO);
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
}
