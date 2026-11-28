package bipotheking.net.duora.push.infra;

import bipotheking.net.duora.push.EnvioDePush;
import bipotheking.net.duora.push.InscricaoDePush;
import bipotheking.net.duora.push.ResultadoDoEnvio;
import bipotheking.net.duora.push.TipoDePush;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envio com o push desligado ({@code duora.push.habilitado=false}): escreve só o id da inscrição no
 * log e responde {@link ResultadoDoEnvio#ENTREGUE}, para a fila de avisos andar normalmente.
 */
final class EnvioDePushNoLog implements EnvioDePush {

  private static final Logger LOG = LoggerFactory.getLogger(EnvioDePushNoLog.class);

  @Override
  public ResultadoDoEnvio enviar(InscricaoDePush inscricao, TipoDePush tipo) {
    LOG.info("Push desligado: aviso para a inscrição {} só no log", inscricao.id());
    return ResultadoDoEnvio.ENTREGUE;
  }
}
