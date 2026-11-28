package bipotheking.net.duora.perfil;

/**
 * Orientação que a pessoa escolhe informar: dado sensível. {@code OUTRA} pede uma descrição curta.
 */
public enum Orientacao {
  HETEROSSEXUAL("Heterossexual"),
  GAY("Gay"),
  LESBICA("Lésbica"),
  BISSEXUAL("Bissexual"),
  PANSEXUAL("Pansexual"),
  ASSEXUAL("Assexual"),
  DEMISSEXUAL("Demissexual"),
  QUEER("Queer"),
  QUESTIONANDO("Questionando"),
  OUTRA("Prefiro descrever");

  private final String rotulo;

  Orientacao(String rotulo) {
    this.rotulo = rotulo;
  }

  /** O texto da opção, como a tela o mostra. */
  public String rotulo() {
    return rotulo;
  }
}
