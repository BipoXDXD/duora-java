package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Cenario;
import bipotheking.net.duora.sala.dominio.SorteioDeCenario;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

/**
 * Escolhe o cenário de um encontro entre os sorteáveis do catálogo ({@code
 * duora.cenarios.sorteaveis}), preferindo o que ninguém do par jogou.
 */
@Service
public class EscolhaDeCenario {

  private final CatalogoDeCenarios catalogo;
  private final HistoricoDeCenarios historico;
  private final RandomGenerator aleatorio;

  EscolhaDeCenario(
      CatalogoDeCenarios catalogo, HistoricoDeCenarios historico, RandomGenerator aleatorio) {
    this.catalogo = catalogo;
    this.historico = historico;
    this.aleatorio = aleatorio;
  }

  /** O cenário do encontro pareado: o sorteável jogado por menos pessoas do par. */
  public String sortearPara(ContaId a, ContaId b) {
    return SorteioDeCenario.sortear(historico.jogados(a), historico.jogados(b), ids(), aleatorio);
  }

  /** O cenário do convite privado sem escolha: o sorteio só conhece o histórico de quem cria. */
  public String sortearPara(ContaId criador) {
    return SorteioDeCenario.sortear(historico.jogados(criador), Set.of(), ids(), aleatorio);
  }

  public boolean sorteavel(String cenarioId) {
    return ids().contains(cenarioId);
  }

  /** Os sorteáveis, na ordem do catálogo. */
  public List<Cenario> sorteaveis() {
    return catalogo.sorteaveis();
  }

  private List<String> ids() {
    return catalogo.sorteaveis().stream().map(Cenario::id).toList();
  }
}
