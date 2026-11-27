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
  EXCLUIDA;

  /**
   * A conta pode usar o app: as outras recebem {@code CONTA_INDISPONIVEL} em toda rota. O {@code
   * switch} exaustivo obriga quem criar um estado novo a classificá-lo aqui.
   */
  public boolean disponivel() {
    return switch (this) {
      case PENDENTE, AGUARDANDO_VERIFICACAO, ATIVA -> true;
      case SUSPENSA, BANIDA, EXCLUIDA -> false;
    };
  }

  /**
   * A conta passou pela ativação. Entre as disponíveis, só {@code ATIVA}; as demais rotas
   * protegidas, sem {@code @ContaAtual(cadastroConcluido = false)}, exigem isto.
   */
  public boolean cadastroConcluido() {
    return switch (this) {
      case ATIVA, SUSPENSA, BANIDA -> true;
      case PENDENTE, AGUARDANDO_VERIFICACAO, EXCLUIDA -> false;
    };
  }
}
