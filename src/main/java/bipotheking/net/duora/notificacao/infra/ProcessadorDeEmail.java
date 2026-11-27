package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.EnvioDeEmail;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.notificacao.ProcessadorDeOutbox;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Entrega as mensagens {@link Outbox#EMAIL}, com o id da mensagem como chave de idempotência. */
@Component
class ProcessadorDeEmail implements ProcessadorDeOutbox<MensagemDeEmail> {

  private final EnvioDeEmail envio;

  ProcessadorDeEmail(EnvioDeEmail envio) {
    this.envio = envio;
  }

  @Override
  public String tipo() {
    return Outbox.EMAIL;
  }

  @Override
  public Class<MensagemDeEmail> tipoDaCarga() {
    return MensagemDeEmail.class;
  }

  @Override
  public void processar(MensagemDeEmail carga, UUID idDaMensagem) {
    envio.enviar(carga, idDaMensagem);
  }
}
