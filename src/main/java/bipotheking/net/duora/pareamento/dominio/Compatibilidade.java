package bipotheking.net.duora.pareamento.dominio;

import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Preferencias;
import java.util.Collections;
import java.util.EnumSet;
import java.util.OptionalDouble;
import java.util.Set;

/** Se duas pessoas da fila podem se encontrar: tudo vale nos dois sentidos. */
public final class Compatibilidade {

  private Compatibilidade() {}

  /**
   * As intenções que as duas têm. Namoro só fica se as duas têm gênero próprio e cada uma está
   * entre os gêneros que a outra busca.
   */
  public static Set<Intencao> intencoesEmComum(Preferencias a, Preferencias b) {
    EnumSet<Intencao> comuns = EnumSet.noneOf(Intencao.class);
    comuns.addAll(a.intencao());
    comuns.retainAll(b.intencao());
    if (comuns.contains(Intencao.NAMORO) && !generosCasam(a, b)) {
      comuns.remove(Intencao.NAMORO);
    }
    return Collections.unmodifiableSet(comuns);
  }

  /**
   * Compatíveis quando: as duas são contas de teste ou nenhuma é; há intenção em comum; a idade de
   * cada uma cabe na faixa da outra; e o raio obrigatório de cada lado é respeitado (as duas
   * células conhecidas e a distância dentro do raio).
   */
  public static boolean compativeis(Candidato a, Candidato b) {
    return a.deTeste() == b.deTeste()
        && !intencoesEmComum(a.preferencias(), b.preferencias()).isEmpty()
        && b.preferencias().faixaEtaria().contem(a.idade())
        && a.preferencias().faixaEtaria().contem(b.idade())
        && dentroDoRaio(a, b)
        && dentroDoRaio(b, a);
  }

  /** A distância entre as células das duas, vazia se alguma não tem célula. */
  public static OptionalDouble distanciaKm(Candidato a, Candidato b) {
    if (a.celula().isEmpty() || b.celula().isEmpty()) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(a.celula().get().distanciaKm(b.celula().get()));
  }

  private static boolean generosCasam(Preferencias a, Preferencias b) {
    return a.generoProprio().isPresent()
        && b.generoProprio().isPresent()
        && b.generosBuscados().contains(a.generoProprio().get())
        && a.generosBuscados().contains(b.generoProprio().get());
  }

  /** O raio obrigatório de {@code quemLimita} aceita {@code outro}. */
  private static boolean dentroDoRaio(Candidato quemLimita, Candidato outro) {
    Preferencias preferencias = quemLimita.preferencias();
    if (!preferencias.raioObrigatorio()) {
      return true;
    }
    OptionalDouble distancia = distanciaKm(quemLimita, outro);
    return distancia.isPresent()
        && preferencias.raio().km().isPresent()
        && distancia.getAsDouble() <= preferencias.raio().km().getAsInt();
  }
}
