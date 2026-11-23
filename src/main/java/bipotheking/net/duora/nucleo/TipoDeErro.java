package bipotheking.net.duora.nucleo;

/** Natureza de uma recusa, sem saber de HTTP. Só a borda web traduz cada tipo para um status. */
public enum TipoDeErro {
  ENTRADA_INVALIDA,
  NAO_AUTENTICADO,
  PROIBIDO,
  NAO_ENCONTRADO,
  METODO_NAO_PERMITIDO,
  CONFLITO,
  ENCERRADO,
  GRANDE_DEMAIS,
  FORMATO_NAO_SUPORTADO,
  ENDERECO_RECUSADO,
  REGRA_DE_NEGOCIO,
  LIMITE,
  INTERNO,
  INDISPONIVEL
}
