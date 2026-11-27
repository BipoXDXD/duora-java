package bipotheking.net.duora.arquitetura.exemplos.moduloe;

import bipotheking.net.duora.arquitetura.exemplos.modulod.Ida;

/** A outra metade do ciclo: {@code moduloe} usa {@code modulod}. */
public class Volta {

  public Ida ida() {
    return new Ida();
  }
}
