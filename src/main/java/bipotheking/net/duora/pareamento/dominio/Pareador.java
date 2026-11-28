package bipotheking.net.duora.pareamento.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.pareamento.ParDeContas;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.pareamento.ResultadoDoCiclo;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Um ciclo de pareamento, sem estado: quem chegou primeiro escolhe primeiro e leva, entre as
 * compatíveis ainda sem par e sem veto, quem espera há mais ciclos, depois a mais perto (distância
 * desconhecida por último), depois quem entrou antes e, no empate, a menor conta. O resultado não
 * depende da ordem da lista de entrada.
 */
public final class Pareador {

  /** Ordem de chegada: quem entrou antes e, no empate, a menor conta sem sinal. */
  private static final Comparator<Candidato> CHEGADA =
      Comparator.comparing(Candidato::entrouEm)
          .thenComparing(Candidato::conta, ParDeContas.ORDEM_SEM_SINAL);

  private Pareador() {}

  /**
   * @param vagas quantos pares, no máximo, o ciclo pode formar
   * @throws IllegalArgumentException se {@code vagas} é negativo ou {@code ciclo} não é positivo
   */
  public static ResultadoDoCiclo parear(
      List<Candidato> fila, Set<ParDeContas> vetados, int vagas, Instant agora, Duration ciclo) {
    Objects.requireNonNull(fila, "fila");
    Objects.requireNonNull(vetados, "vetados");
    Objects.requireNonNull(agora, "agora");
    Objects.requireNonNull(ciclo, "ciclo");
    if (vagas < 0) {
      throw new IllegalArgumentException("Vagas negativas");
    }
    if (ciclo.isZero() || ciclo.isNegative()) {
      throw new IllegalArgumentException("Ciclo não positivo");
    }
    List<Candidato> ordenada = fila.stream().sorted(CHEGADA).toList();
    Set<ContaId> pareadas = new HashSet<>();
    List<ParPareado> pares = new ArrayList<>();
    for (Candidato quemEscolhe : ordenada) {
      if (pares.size() >= vagas) {
        break;
      }
      if (pareadas.contains(quemEscolhe.conta())) {
        continue;
      }
      Optional<Candidato> escolhida =
          ordenada.stream()
              .filter(outra -> !outra.conta().equals(quemEscolhe.conta()))
              .filter(outra -> !pareadas.contains(outra.conta()))
              .filter(
                  outra -> !vetados.contains(ParDeContas.de(quemEscolhe.conta(), outra.conta())))
              .filter(outra -> Compatibilidade.compativeis(quemEscolhe, outra))
              .min(preferenciaDe(quemEscolhe, agora, ciclo));
      escolhida.ifPresent(parceira -> {
        pareadas.add(quemEscolhe.conta());
        pareadas.add(parceira.conta());
        pares.add(parear(quemEscolhe, parceira));
      });
    }
    List<Candidato> semPar = ordenada.stream().filter(c -> !pareadas.contains(c.conta())).toList();
    return new ResultadoDoCiclo(pares, semPar);
  }

  private static ParPareado parear(Candidato um, Candidato outro) {
    boolean umChegouAntes = CHEGADA.compare(um, outro) <= 0;
    Candidato a = umChegouAntes ? um : outro;
    Candidato b = umChegouAntes ? outro : um;
    return new ParPareado(
        a.conta(), b.conta(), Compatibilidade.intencoesEmComum(a.preferencias(), b.preferencias()));
  }

  /**
   * A melhor parceira vem primeiro: mais ciclos de espera, mais perto, chegou antes, menor conta.
   */
  private static Comparator<Candidato> preferenciaDe(
      Candidato quemEscolhe, Instant agora, Duration ciclo) {
    Comparator<Candidato> maisCiclos =
        Comparator.comparingLong(
            (Candidato outra) -> Duration.between(outra.entrouEm(), agora).dividedBy(ciclo));
    Comparator<Candidato> maisPerto =
        Comparator.comparingDouble(outra ->
            Compatibilidade.distanciaKm(quemEscolhe, outra).orElse(Double.POSITIVE_INFINITY));
    return maisCiclos.reversed().thenComparing(maisPerto).thenComparing(CHEGADA);
  }
}
