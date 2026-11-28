package bipotheking.net.duora.perfil.dominio;

/**
 * Toda foto nova entra {@code PENDENTE}; a moderação a leva a {@code APROVADA} ou {@code RECUSADA}.
 */
public enum EstadoDaFoto {
  PENDENTE,
  APROVADA,
  RECUSADA
}
