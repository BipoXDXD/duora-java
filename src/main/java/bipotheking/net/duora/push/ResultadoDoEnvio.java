package bipotheking.net.duora.push;

/** Como o serviço de push respondeu a um envio. */
public enum ResultadoDoEnvio {
  /** Aceito (2xx). */
  ENTREGUE,
  /** A inscrição não existe mais no serviço (404 ou 410): deve ser apagada. */
  EXPIRADA,
  /** Qualquer outra resposta, endpoint recusado ou erro de rede. */
  FALHOU
}
