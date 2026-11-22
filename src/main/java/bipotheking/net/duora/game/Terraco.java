package bipotheking.net.duora.game;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** O terraço das estrelas cadentes, o primeiro cenário. */
public final class Terraco {

  public static final String ID = "terraco";

  // Nesta fase as constantes apontam para a fachada de Zona, Lugar e Peca; o sentido contrário
  // (Peca.TELESCOPIO = Terraco.TELESCOPIO) faria Peca ler null se Terraco inicializasse primeiro.
  public static final Zona CENTRO = Zona.CENTRO;
  public static final Zona LATERAL = Zona.LATERAL;

  public static final Lugar C1 = Lugar.C1;
  public static final Lugar C2 = Lugar.C2;
  public static final Lugar L1 = Lugar.L1;
  public static final Lugar L2 = Lugar.L2;
  public static final Lugar R1 = Lugar.R1;
  public static final Lugar R2 = Lugar.R2;

  public static final Peca TELESCOPIO = Peca.TELESCOPIO;
  public static final Peca MANTA = Peca.MANTA;
  public static final Peca RADIO = Peca.RADIO;
  public static final Peca BANCO = Peca.BANCO;
  public static final Peca LANTERNA = Peca.LANTERNA;
  public static final Peca GARRAFA = Peca.GARRAFA;

  public static final Cenario CENARIO =
      new Cenario(
          ID,
          "Terraço das Estrelas Cadentes",
          List.of(C1, C2, L1, L2, R1, R2),
          List.of(TELESCOPIO, MANTA, RADIO, BANCO, LANTERNA, GARRAFA),
          1,
          Duration.ofSeconds(20),
          2,
          Terraco::desfecho);

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
