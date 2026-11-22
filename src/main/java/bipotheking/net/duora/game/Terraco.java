package bipotheking.net.duora.game;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** O terraço das estrelas cadentes, o primeiro cenário. */
public final class Terraco {

  public static final String ID = "terraco";

  public static final Zona CENTRO = new Zona("centro");
  public static final Zona LATERAL = new Zona("lateral");

  public static final Lugar C1 = new Lugar("C1", CENTRO);
  public static final Lugar C2 = new Lugar("C2", CENTRO);
  public static final Lugar L1 = new Lugar("L1", LATERAL);
  public static final Lugar L2 = new Lugar("L2", LATERAL);
  public static final Lugar R1 = new Lugar("R1", LATERAL);
  public static final Lugar R2 = new Lugar("R2", LATERAL);

  public static final Peca TELESCOPIO = new Peca("telescopio", Assento.A, CENTRO, true);
  public static final Peca MANTA = new Peca("manta", Assento.A, LATERAL, false);
  public static final Peca RADIO = new Peca("radio", Assento.A, LATERAL, false);
  public static final Peca BANCO = new Peca("banco", Assento.B, CENTRO, true);
  public static final Peca LANTERNA = new Peca("lanterna", Assento.B, LATERAL, false);
  public static final Peca GARRAFA = new Peca("garrafa", Assento.B, LATERAL, false);

  public static final Cenario CENARIO =
      new Cenario(
          ID,
          "Terraço das Estrelas Cadentes",
          List.of(C1, C2, L1, L2, R1, R2),
          List.of(TELESCOPIO, MANTA, RADIO, BANCO, LANTERNA, GARRAFA),
          1,
          Duration.ofSeconds(20),
          2,
          Terraco::desfecho,
          Optional.of(
              new DefinicaoDeAbertura(
                  "acender-junto", Duration.ofSeconds(5), Duration.ofSeconds(90))));

  private Terraco() {}

  /** A secundária colocada de cada pessoa e o lado do céu para onde o telescópio aponta. */
  private static Resultado desfecho(Composicao composicao, Cenario cenario) {
    Map<String, String> valores = new LinkedHashMap<>();
    valores.put("secundariaA", secundariaColocada(composicao, cenario, Assento.A).id());
    valores.put("secundariaB", secundariaColocada(composicao, cenario, Assento.B).id());
    boolean esquerdo = composicao.lugarDe(TELESCOPIO).orElseThrow().equals(C1);
    valores.put("ladoTelescopio", esquerdo ? "esquerdo" : "direito");
    return new Resultado(valores);
  }

  private static Peca secundariaColocada(Composicao composicao, Cenario cenario, Assento dono) {
    return cenario.secundariasDe(dono).stream()
        .filter(composicao::colocada)
        .findFirst()
        .orElseThrow();
  }
}
