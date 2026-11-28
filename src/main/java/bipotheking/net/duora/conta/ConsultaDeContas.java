package bipotheking.net.duora.conta;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/** Leitura de contas para outros módulos, sem expor o modelo de domínio. */
public interface ConsultaDeContas {

  Optional<ContaResumo> porId(ContaId id);

  /**
   * As contas pedidas que existem, em lote (uma consulta das contas e uma do selo). Os ids que não
   * existem ficam de fora do mapa.
   */
  Map<ContaId, ContaResumo> porIds(Collection<ContaId> ids);
}
