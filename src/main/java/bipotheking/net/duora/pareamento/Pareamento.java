package bipotheking.net.duora.pareamento;

import bipotheking.net.duora.pareamento.dominio.Compatibilidade;
import bipotheking.net.duora.pareamento.dominio.Pareador;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Preferencias;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/** A fachada do pareamento para os outros módulos: o domínio puro com os vetos do banco. */
@Service
public class Pareamento {

  private final VetosDePar vetos;

  Pareamento(VetosDePar vetos) {
    this.vetos = vetos;
  }

  /**
   * Um ciclo de pareamento sobre a fila, lendo os vetos entre as contas dela numa consulta.
   *
   * @see Pareador#parear
   */
  public ResultadoDoCiclo parear(List<Candidato> fila, int vagas, Instant agora, Duration ciclo) {
    Set<ParDeContas> vetados = vetos.vetadosEntre(fila.stream().map(Candidato::conta).toList());
    return Pareador.parear(fila, vetados, vagas, agora, ciclo);
  }

  /**
   * @see Compatibilidade#compativeis
   */
  public boolean compativeis(Candidato a, Candidato b) {
    return Compatibilidade.compativeis(a, b);
  }

  /**
   * @see Compatibilidade#intencoesEmComum
   */
  public Set<Intencao> intencoesEmComum(Preferencias a, Preferencias b) {
    return Compatibilidade.intencoesEmComum(a, b);
  }
}
