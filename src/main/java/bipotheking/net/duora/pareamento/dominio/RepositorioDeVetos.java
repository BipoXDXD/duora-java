package bipotheking.net.duora.pareamento.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.MotivoVeto;
import bipotheking.net.duora.pareamento.ParDeContas;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** Os pares vetados (tabela {@code par_vetado}, V51). */
public interface RepositorioDeVetos {

  /**
   * Grava o veto. Se o par já tem veto, só um veto real sobre {@link MotivoVeto#CONECTADOS} o
   * troca; nos outros casos fica o que já estava.
   */
  void vetar(ParDeContas par, MotivoVeto motivo, Instant agora);

  Optional<MotivoVeto> motivo(ParDeContas par);

  /** Os pares vetados com as duas contas no conjunto dado, numa consulta só. */
  Set<ParDeContas> vetadosEntre(Collection<ContaId> contas);
}
