package bipotheking.net.duora.pareamento;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.dominio.RepositorioDeVetos;
import java.time.Clock;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Pares que não voltam a ser pareados, nos dois sentidos, até a exclusão de uma das contas. */
@Service
public class VetosDePar {

  private final RepositorioDeVetos repositorio;
  private final Clock relogio;

  VetosDePar(RepositorioDeVetos repositorio, Clock relogio) {
    this.repositorio = repositorio;
    this.relogio = relogio;
  }

  /**
   * Veta o par. Um veto real substitui {@link MotivoVeto#CONECTADOS}; {@code CONECTADOS} nunca
   * substitui um veto real, e um segundo veto real não troca o primeiro.
   */
  public void vetar(ParDeContas par, MotivoVeto motivo) {
    repositorio.vetar(par, motivo, relogio.instant());
  }

  public Optional<MotivoVeto> motivo(ParDeContas par) {
    return repositorio.motivo(par);
  }

  /** Os pares vetados entre as contas dadas, numa consulta; sem contas, nem consulta. */
  public Set<ParDeContas> vetadosEntre(Collection<ContaId> contas) {
    if (contas.isEmpty()) {
      return Set.of();
    }
    return repositorio.vetadosEntre(contas);
  }
}
