package bipotheking.net.duora.arquitetura.exemplos.modulod;

import bipotheking.net.duora.arquitetura.exemplos.moduloe.Volta;

/**
 * Metade de um ciclo entre módulos: {@code modulod} usa {@code moduloe}, que usa {@code modulod}.
 */
public class Ida {

  public Volta volta() {
    return new Volta();
  }
}
