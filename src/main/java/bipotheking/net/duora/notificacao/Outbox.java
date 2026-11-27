package bipotheking.net.duora.notificacao;

/**
 * Porta da outbox transacional: a mensagem entra na transação do fato e sai depois, pelo
 * despachante, com retry e backoff. Quem registra não sabe como nem quando ela é entregue.
 */
public interface Outbox {

  /** Tipo das mensagens de e-mail. */
  String EMAIL = "email";

  /**
   * Registra a mensagem na transação corrente, com o correlation ID do MDC. Se a transação voltar
   * atrás, a mensagem some com ela.
   *
   * @param tipo escolhe o {@link ProcessadorDeOutbox} que vai entregá-la
   * @param titular chave opaca de quem a mensagem trata (para {@link #esquecerTitular})
   * @param carga serializada em JSON; existe só enquanto a mensagem está pendente
   * @throws org.springframework.transaction.IllegalTransactionStateException sem transação aberta
   */
  <T> void registrar(String tipo, String titular, T carga);

  /**
   * Apaga a carga das mensagens pendentes do titular, que passam a falhadas sem entrega.
   *
   * @return quantas mensagens pendentes foram esquecidas
   */
  int esquecerTitular(String titular);
}
