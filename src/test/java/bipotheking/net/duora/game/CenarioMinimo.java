package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Cenário mínimo para testes: uma zona, dois lugares e uma peça obrigatória por pessoa. {@link
 * #COM_ABERTURA} é o mesmo cenário com a abertura de acender junto.
 */
public final class CenarioMinimo {

  public static final Zona ZONA = new Zona("unica");
  public static final Lugar P1 = new Lugar("P1", ZONA);
  public static final Lugar P2 = new Lugar("P2", ZONA);
  public static final Peca PEDRA = new Peca("pedra", A, ZONA, true);
  public static final Peca FOLHA = new Peca("folha", B, ZONA, true);

  public static final Cenario CENARIO =
      new Cenario(
          "minimo",
          "Mínimo",
          List.of(P1, P2),
          List.of(PEDRA, FOLHA),
          1,
          Duration.ofSeconds(20),
          1,
          (composicao, cenario) -> new Resultado(Map.of("juntos", "sim")));

  public static final Cenario COM_ABERTURA =
      new Cenario(
          "minimo-abertura",
          "Mínimo com abertura",
          List.of(P1, P2),
          List.of(PEDRA, FOLHA),
          1,
          Duration.ofSeconds(20),
          1,
          (composicao, cenario) -> new Resultado(Map.of("juntos", "sim")),
          Optional.of(
              new DefinicaoDeAbertura(
                  "acender-junto", Duration.ofSeconds(5), Duration.ofSeconds(90))));

  private CenarioMinimo() {}
}
