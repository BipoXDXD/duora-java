package bipotheking.net.duora.pareamento;

/**
 * Por que um par não volta a ser pareado. {@link #CONECTADOS} não é um veto real: um veto real o
 * substitui, nunca o contrário.
 */
public enum MotivoVeto {
  NAO,
  SAIDA,
  DENUNCIA,
  BLOQUEIO,
  CONECTADOS,
  SEM_DECISAO
}
