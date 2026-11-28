package bipotheking.net.duora.perfil;

/** Pronomes que a pessoa escolhe mostrar. {@code OUTROS} pede uma descrição curta. */
public enum Pronomes {
  ELA_DELA("ela/dela"),
  ELE_DELE("ele/dele"),
  ELU_DELU("elu/delu"),
  OUTROS("Prefiro descrever");

  private final String rotulo;

  Pronomes(String rotulo) {
    this.rotulo = rotulo;
  }

  /** O texto da opção, como a tela o mostra. */
  public String rotulo() {
    return rotulo;
  }
}
