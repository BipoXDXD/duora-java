package bipotheking.net.duora.push;

/** Porta do envio de um Web Push a uma inscrição. Não lança: o resultado diz o que houve. */
public interface EnvioDePush {

  ResultadoDoEnvio enviar(InscricaoDePush inscricao, TipoDePush tipo);
}
