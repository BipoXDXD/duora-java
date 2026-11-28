package bipotheking.net.duora.sala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.game.CenarioMinimo;
import bipotheking.net.duora.game.Terraco;
import bipotheking.net.duora.sala.dominio.RepositorioDeHistorico;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EscolhaDeCenarioTest {

  private static final List<Cenario> CENARIOS = List.of(Terraco.CENARIO, CenarioMinimo.CENARIO);

  private final ContaId lia = new ContaId(UUID.randomUUID());
  private final ContaId rui = new ContaId(UUID.randomUUID());
  private final HistoricoDeCenarios historico =
      new HistoricoDeCenarios(
          new HistoricoEmMemoria(),
          Clock.fixed(Instant.parse("2026-11-28T12:00:00Z"), ZoneOffset.UTC));

  @Test
  void soTerracoSorteavelDaTerraco() {
    EscolhaDeCenario escolha = escolhaCom(List.of("terraco"));
    historico.registrar(lia, "terraco");

    assertThat(escolha.sortearPara(lia, rui)).isEqualTo("terraco");
    assertThat(escolha.sortearPara(lia)).isEqualTo("terraco");
  }

  @Test
  void parEvitaOCenarioQueUmDelesJaJogou() {
    EscolhaDeCenario escolha = escolhaCom(List.of("terraco", "minimo"));
    historico.registrar(rui, "terraco");

    assertThat(escolha.sortearPara(lia, rui)).isEqualTo("minimo");
  }

  @Test
  void criadorSoOlhaOProprioHistorico() {
    EscolhaDeCenario escolha = escolhaCom(List.of("terraco", "minimo"));
    historico.registrar(lia, "minimo");

    assertThat(escolha.sortearPara(lia)).isEqualTo("terraco");
  }

  @Test
  void cenarioForaDaListaNaoESorteavel() {
    EscolhaDeCenario escolha = escolhaCom(List.of("terraco"));

    assertThat(escolha.sorteavel("terraco")).isTrue();
    assertThat(escolha.sorteavel("minimo")).isFalse();
    assertThat(escolha.sorteavel("vulcao")).isFalse();
  }

  @Test
  void sorteaveisTrazIdENome() {
    EscolhaDeCenario escolha = escolhaCom(List.of("minimo", "terraco"));

    assertThat(escolha.sorteaveis())
        .extracting(Cenario::id, Cenario::nome)
        .containsExactly(
            tuple("minimo", CenarioMinimo.CENARIO.nome()),
            tuple("terraco", Terraco.CENARIO.nome()));
  }

  private EscolhaDeCenario escolhaCom(List<String> sorteaveis) {
    return new EscolhaDeCenario(
        new CatalogoDeCenarios(CENARIOS, sorteaveis), historico, new SplittableRandom(42));
  }

  private static final class HistoricoEmMemoria implements RepositorioDeHistorico {

    private final Map<ContaId, Set<String>> jogados = new HashMap<>();

    @Override
    public void registrar(ContaId conta, String cenarioId, Instant agora) {
      jogados.computeIfAbsent(conta, chave -> new HashSet<>()).add(cenarioId);
    }

    @Override
    public Set<String> jogados(ContaId conta) {
      return Set.copyOf(jogados.getOrDefault(conta, Set.of()));
    }
  }
}
