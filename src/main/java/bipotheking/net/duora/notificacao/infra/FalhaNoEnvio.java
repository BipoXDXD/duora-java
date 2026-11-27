package bipotheking.net.duora.notificacao.infra;

import java.io.Serial;

/**
 * O provedor de e-mail não confirmou a entrega. Falha técnica: a outbox tenta de novo. A mensagem
 * vai para {@code outbox.ultimo_erro} e nunca leva destinatário, chave nem trecho do corpo.
 */
class FalhaNoEnvio extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  FalhaNoEnvio(String mensagem) {
    super(mensagem);
  }
}
