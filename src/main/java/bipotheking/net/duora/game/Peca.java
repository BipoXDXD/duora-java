package bipotheking.net.duora.game;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * As seis peças. Cada pessoa tem uma obrigatória (zona centro) e duas secundárias (zona lateral),
 * das quais só uma pode estar no terraço.
 */
public enum Peca {
  TELESCOPIO(Assento.A, Zona.CENTRO),
  MANTA(Assento.A, Zona.LATERAL),
  RADIO(Assento.A, Zona.LATERAL),
  BANCO(Assento.B, Zona.CENTRO),
  LANTERNA(Assento.B, Zona.LATERAL),
  GARRAFA(Assento.B, Zona.LATERAL);

  private final Assento dono;
  private final Zona zona;

  Peca(Assento dono, Zona zona) {
    this.dono = dono;
    this.zona = zona;
  }

  public Assento dono() {
    return dono;
  }

  public Zona zona() {
    return zona;
  }

  public boolean obrigatoria() {
    return zona == Zona.CENTRO;
  }

  public boolean aceita(Lugar lugar) {
    return lugar.zona() == zona;
  }

  public String id() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static Optional<Peca> deId(String id) {
    return Arrays.stream(values()).filter(peca -> peca.id().equals(id)).findFirst();
  }

  public static List<Peca> de(Assento dono) {
    return Arrays.stream(values()).filter(peca -> peca.dono == dono).toList();
  }

  public static List<Peca> secundariasDe(Assento dono) {
    return de(dono).stream().filter(peca -> !peca.obrigatoria()).toList();
  }
}
