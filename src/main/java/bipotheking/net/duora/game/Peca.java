package bipotheking.net.duora.game;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Uma peça de um cenário, de um dono, que só vai para lugares da sua zona. No terraço, cada pessoa
 * tem uma obrigatória (zona centro) e duas secundárias (zona lateral), das quais só uma pode estar
 * no terraço.
 */
public record Peca(String id, Assento dono, Zona zona, boolean obrigatoria) {

  /** Fachada provisória das seis peças do terraço: some quando o cenário fornecer as peças. */
  public static final Peca TELESCOPIO = new Peca("telescopio", Assento.A, Zona.CENTRO, true);

  public static final Peca MANTA = new Peca("manta", Assento.A, Zona.LATERAL, false);
  public static final Peca RADIO = new Peca("radio", Assento.A, Zona.LATERAL, false);
  public static final Peca BANCO = new Peca("banco", Assento.B, Zona.CENTRO, true);
  public static final Peca LANTERNA = new Peca("lanterna", Assento.B, Zona.LATERAL, false);
  public static final Peca GARRAFA = new Peca("garrafa", Assento.B, Zona.LATERAL, false);

  private static final Peca[] TERRACO = {TELESCOPIO, MANTA, RADIO, BANCO, LANTERNA, GARRAFA};

  public Peca {
    Objects.requireNonNull(id);
    Objects.requireNonNull(dono);
    Objects.requireNonNull(zona);
    if (id.isBlank()) {
      throw new IllegalArgumentException("id de peça vazio");
    }
  }

  public boolean aceita(Lugar lugar) {
    return lugar.zona().equals(zona);
  }

  public static Peca[] values() {
    return TERRACO.clone();
  }

  public static Optional<Peca> deId(String id) {
    return Arrays.stream(TERRACO).filter(peca -> peca.id.equals(id)).findFirst();
  }

  public static List<Peca> de(Assento dono) {
    return Arrays.stream(TERRACO).filter(peca -> peca.dono == dono).toList();
  }

  public static List<Peca> secundariasDe(Assento dono) {
    return de(dono).stream().filter(peca -> !peca.obrigatoria).toList();
  }

  /**
   * Peça do terraço pelo nome da antiga constante (ex. {@code "TELESCOPIO"}), como no {@code
   * Enum.valueOf}. É a única fábrica {@code String → Peca}: o JUnit a usa para converter o
   * {@code @CsvSource}.
   */
  public static Peca valueOf(String nome) {
    return Arrays.stream(TERRACO)
        .filter(peca -> peca.id.toUpperCase(Locale.ROOT).equals(nome))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("peça desconhecida: " + nome));
  }
}
