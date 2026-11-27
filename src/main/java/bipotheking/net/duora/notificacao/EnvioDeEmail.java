package bipotheking.net.duora.notificacao;

import java.util.UUID;

/**
 * Porta de saída do e-mail. Quem envia pela aplicação usa a {@link Outbox}; só o processador da
 * outbox chama esta porta, fora de transação de banco.
 */
public interface EnvioDeEmail {

  /**
   * Entrega a mensagem ao provedor. Pode ser chamada de novo com a mesma chave (retry): o provedor
   * deve tratar a segunda chamada como repetição da primeira.
   *
   * @throws RuntimeException se o provedor não confirmou a entrega; a mensagem da exceção nunca
   *     leva o destinatário nem credencial
   */
  void enviar(MensagemDeEmail mensagem, UUID chaveDeIdempotencia);
}
