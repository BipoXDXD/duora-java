package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Terraco.BANCO;
import static bipotheking.net.duora.game.Terraco.C1;
import static bipotheking.net.duora.game.Terraco.C2;
import static bipotheking.net.duora.game.Terraco.CENARIO;
import static bipotheking.net.duora.game.Terraco.L1;
import static bipotheking.net.duora.game.Terraco.LANTERNA;
import static bipotheking.net.duora.game.Terraco.MANTA;
import static bipotheking.net.duora.game.Terraco.R1;
import static bipotheking.net.duora.game.Terraco.TELESCOPIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O {@code cenario.json} e o {@code cena/cena.json} do terraço, servidos ao front, espelham o
 * {@link Terraco#CENARIO}: os valores esperados saem do lado Java, nunca de cópias à mão.
 */
class CenarioJsonTest {

  private static final Path PASTA =
      Path.of("src", "main", "resources", "static", "assets", "cenarios", Terraco.ID);
  private static final JsonNode JSON = ler(PASTA.resolve("cenario.json"));
  private static final JsonNode CENA = ler(PASTA.resolve("cena").resolve("cena.json"));

  /** Marcador da legenda: {@code {chave}} ou {@code {chave.campo}}, como em {@code legenda.ts}. */
  private static final Pattern MARCADOR = Pattern.compile("\\{([^{}.]*)(?:\\.([^{}]*))?}");

  private static final Set<String> NOMES_DAS_PESSOAS = Set.of("nomeA", "nomeB");

  /** O que o front lê de cada peça e o motor também define. */
  private record PecaEspelhada(String id, String assento, String zona, boolean obrigatoria) {}

  @Test
  void idENomeIguaisAoCenario() {
    assertThat(List.of(JSON.get("id").stringValue(), JSON.get("nome").stringValue()))
        .containsExactly(CENARIO.id(), CENARIO.nome());
  }

  @Test
  void pecasIguaisAoCenario() {
    List<PecaEspelhada> doJson =
        JSON.get("pecas").properties().stream()
            .map(peca ->
                new PecaEspelhada(
                    peca.getKey(),
                    peca.getValue().get("assento").stringValue(),
                    peca.getValue().get("zona").stringValue(),
                    peca.getValue().get("obrigatoria").booleanValue()))
            .toList();
    List<PecaEspelhada> doCenario =
        CENARIO.pecas().stream()
            .map(peca ->
                new PecaEspelhada(
                    peca.id(), peca.dono().name(), peca.zona().id(), peca.obrigatoria()))
            .toList();

    assertThat(doJson).containsExactlyElementsOf(doCenario);
  }

  @Test
  void lugaresIguaisAoCenario() {
    assertThat(JSON.get("lugares").propertyNames()).containsExactlyElementsOf(idsDosLugares());
  }

  @Test
  void zonasIguaisAsDosLugaresDoCenario() {
    List<String> zonas =
        CENARIO.lugares().stream().map(lugar -> lugar.zona().id()).distinct().toList();

    assertThat(JSON.get("zonas").propertyNames()).containsExactlyElementsOf(zonas);
  }

  @Test
  void cenaTemOPontoDeCadaLugarDoCenario() {
    assertThat(CENA.get("lugares").propertyNames()).containsExactlyElementsOf(idsDosLugares());
  }

  @Test
  void legendaSoUsaChavesDoResultadoENomesDasPessoas() {
    Set<String> conhecidas = new HashSet<>(resultadoDeExemplo().keySet());
    conhecidas.addAll(NOMES_DAS_PESSOAS);

    assertThat(marcadoresDaLegenda())
        .extracting(marcador -> marcador.group(1))
        .isSubsetOf(conhecidas);
  }

  @Test
  void legendaSoLeCampoDePecaDeChaveQueGuardaUmaPeca() {
    Map<String, String> resultado = resultadoDeExemplo();

    assertThat(marcadoresDaLegenda())
        .filteredOn(marcador -> marcador.group(2) != null)
        .extracting(marcador -> resultado.get(marcador.group(1)))
        .isNotEmpty()
        .allSatisfy(valor -> assertThat(CENARIO.peca(valor)).isPresent());
  }

  private static List<String> idsDosLugares() {
    return CENARIO.lugares().stream().map(Lugar::id).toList();
  }

  /** O resultado do desfecho do terraço para uma composição completa qualquer. */
  private static Map<String, String> resultadoDeExemplo() {
    Composicao completa =
        Composicao.vazia().com(TELESCOPIO, C1).com(BANCO, C2).com(MANTA, L1).com(LANTERNA, R1);
    return CENARIO.desfecho().de(completa, CENARIO).valores();
  }

  private static List<MatchResult> marcadoresDaLegenda() {
    JsonNode legenda = JSON.get("legenda");
    String modelos = legenda.get("titulo").stringValue() + legenda.get("texto").stringValue();
    return MARCADOR.matcher(modelos).results().toList();
  }

  private static JsonNode ler(Path arquivo) {
    try {
      return JsonMapper.builder().build().readTree(Files.readString(arquivo));
    } catch (IOException erro) {
      throw new UncheckedIOException(erro);
    }
  }
}
