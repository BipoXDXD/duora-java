package bipotheking.net.duora.pareamento;

import java.util.List;

/** O que um ciclo de pareamento decidiu: os pares formados e quem continua esperando. */
public record ResultadoDoCiclo(List<ParPareado> pares, List<Candidato> semPar) {

  public ResultadoDoCiclo {
    pares = List.copyOf(pares);
    semPar = List.copyOf(semPar);
  }
}
