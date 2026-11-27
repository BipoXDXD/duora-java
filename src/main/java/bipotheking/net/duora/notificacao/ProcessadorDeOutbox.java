package bipotheking.net.duora.notificacao;

import java.util.UUID;

/**
 * Entrega as mensagens de um tipo da {@link Outbox}. Retorno normal marca a mensagem como enviada;
 * exceção conta como falha e agenda nova tentativa. A mesma mensagem pode chegar mais de uma vez
 * (retry, reserva vencida): use {@code idDaMensagem} como chave de idempotência no provedor.
 */
public interface ProcessadorDeOutbox<T> {

  /** O tipo de mensagem que este processador entrega. */
  String tipo();

  /** A classe em que a carga gravada é lida de volta. */
  Class<T> tipoDaCarga();

  /** Entrega a mensagem; chamado fora de transação de banco. */
  void processar(T carga, UUID idDaMensagem);
}
