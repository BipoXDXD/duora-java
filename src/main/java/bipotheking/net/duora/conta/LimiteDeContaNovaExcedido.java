package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.time.Duration;
import java.util.Map;

/** A conta nova chegou ao limite do dia: vira 429 com {@code Retry-After} até a meia-noite. */
public final class LimiteDeContaNovaExcedido extends ErroDeNegocio {

  @Serial private static final long serialVersionUID = 1L;

  LimiteDeContaNovaExcedido(Duration ateAMeiaNoite) {
    super(
        CodigoContaNova.LIMITE_DE_CONTA_NOVA,
        CodigoContaNova.LIMITE_DE_CONTA_NOVA.mensagem(),
        Map.of(),
        ateAMeiaNoite);
  }
}
