package bipotheking.net.duora.conta;

/**
 * Ciclo de vida da conta. {@code PENDENTE} nasce no primeiro link confirmado; o cadastro leva a
 * {@code AGUARDANDO_VERIFICACAO} (já com data de nascimento); a verificação de idade leva a {@code
 * ATIVA}. {@code SUSPENSA} e {@code BANIDA} vêm da moderação; {@code EXCLUIDA} apaga o e-mail.
 */
public enum EstadoConta {
  PENDENTE,
  AGUARDANDO_VERIFICACAO,
  ATIVA,
  SUSPENSA,
  BANIDA,
  EXCLUIDA
}
