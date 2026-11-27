package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.time.Duration;
import java.util.Map;

/** Recusa por limite de taxa: vira 429 com {@code Retry-After}. */
public final class LimiteExcedido extends ErroDeNegocio {

  @Serial private static final long serialVersionUID = 1L;

  private static final long NANOS_POR_SEGUNDO = Duration.ofSeconds(1).toNanos();

  private final long segundosParaTentarDeNovo;

  LimiteExcedido(long nanosParaTentarDeNovo) {
    super(
        CodigoWeb.LIMITE_DE_TAXA,
        "Muitas tentativas. Tente de novo em alguns minutos.",
        Map.of(),
        Duration.ofSeconds(arredondarParaCima(nanosParaTentarDeNovo)));
    this.segundosParaTentarDeNovo = arredondarParaCima(nanosParaTentarDeNovo);
  }

  /** Arredonda para cima: voltar um instante cedo demais seria recusado de novo. */
  private static long arredondarParaCima(long nanos) {
    return Math.ceilDiv(nanos, NANOS_POR_SEGUNDO);
  }

  public long segundosParaTentarDeNovo() {
    return segundosParaTentarDeNovo;
  }
}
