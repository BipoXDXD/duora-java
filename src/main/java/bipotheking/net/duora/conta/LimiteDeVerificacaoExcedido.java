package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.time.Duration;
import java.util.Map;

/**
 * A conta chegou ao limite de tentativas de verificação nas últimas 24 h: vira 429 com {@code
 * Retry-After} até a tentativa mais antiga sair da janela.
 */
public final class LimiteDeVerificacaoExcedido extends ErroDeNegocio {

  @Serial private static final long serialVersionUID = 1L;

  LimiteDeVerificacaoExcedido(Duration ateLiberar) {
    super(
        CodigoVerificacao.LIMITE_DE_VERIFICACAO,
        CodigoVerificacao.LIMITE_DE_VERIFICACAO.mensagem(),
        Map.of(),
        ateLiberar);
  }
}
