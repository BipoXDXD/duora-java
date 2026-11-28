package bipotheking.net.duora.room;

/** Por que a sala virou lápide. */
enum MotivoEncerramento {
  SAIU("A outra pessoa saiu do terraço."),
  EXPIROU("O terraço ficou muito tempo sem atividade e foi encerrado."),
  CANCELADA("A outra pessoa não chegou a tempo.");

  private final String detalhe;

  MotivoEncerramento(String detalhe) {
    this.detalhe = detalhe;
  }

  String detalhe() {
    return detalhe;
  }
}
