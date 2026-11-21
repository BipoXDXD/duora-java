package bipotheking.net.duora.game;

/** Os dois lugares à mesa: quem criou o terraço (A) e quem entrou pelo convite (B). */
public enum Assento {
  A,
  B;

  public Assento parceiro() {
    return this == A ? B : A;
  }
}
