package bipotheking.net.duora.sala;

/** Em que pé está quem entrou na fila. */
public enum SituacaoNaFila {
  /** Há vaga; falta alguém compatível nos dois sentidos. */
  AGUARDANDO_COMPATIVEL,
  /** Todos os encontros estão ocupados: espera a vez pela posição. */
  AGUARDANDO_VAGA,
  /** O par se formou: falta entrar no encontro. */
  PAREADO
}
