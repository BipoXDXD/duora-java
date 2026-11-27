package bipotheking.net.duora.conta.dominio;

/** Desfecho de uma tentativa de verificação de idade. */
public enum ResultadoDaVerificacao {
  /** Maior de idade comprovado: a conta pode ser ativada. */
  APROVADA,
  /** Inconclusivo: a pessoa pode seguir pelo documento. */
  DUVIDA,
  /** Menor de idade. */
  RECUSADA,
  /** A prova não serve para decidir: a pessoa tenta de novo. */
  REPETIR
}
