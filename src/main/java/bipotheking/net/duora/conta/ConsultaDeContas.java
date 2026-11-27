package bipotheking.net.duora.conta;

import java.util.Optional;

/** Leitura de contas para outros módulos, sem expor o modelo de domínio. */
public interface ConsultaDeContas {

  Optional<ContaResumo> porId(ContaId id);
}
