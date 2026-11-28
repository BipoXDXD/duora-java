package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.sala.dominio.RepositorioDeHistorico;
import java.time.Clock;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Os cenários que cada conta já jogou, até a exclusão da conta. */
@Service
public class HistoricoDeCenarios {

  private final RepositorioDeHistorico repositorio;
  private final Clock relogio;

  HistoricoDeCenarios(RepositorioDeHistorico repositorio, Clock relogio) {
    this.repositorio = repositorio;
    this.relogio = relogio;
  }

  /** Marca o cenário como jogado; idempotente (guarda só a primeira vez). */
  public void registrar(ContaId conta, String cenarioId) {
    repositorio.registrar(conta, cenarioId, relogio.instant());
  }

  public Set<String> jogados(ContaId conta) {
    return repositorio.jogados(conta);
  }
}
