package bipotheking.net.duora.pareamento;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.Intencao;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Duas contas que o ciclo juntou e as intenções que elas têm em comum. {@code a} é quem espera há
 * mais tempo.
 */
public record ParPareado(ContaId a, ContaId b, Set<Intencao> intencoes) {

  /**
   * @throws IllegalArgumentException se {@code a} e {@code b} são a mesma conta ou se não há
   *     intenção em comum
   */
  public ParPareado {
    Objects.requireNonNull(a, "a");
    Objects.requireNonNull(b, "b");
    Objects.requireNonNull(intencoes, "intencoes");
    if (a.equals(b)) {
      throw new IllegalArgumentException("Par com a mesma conta");
    }
    if (intencoes.isEmpty()) {
      throw new IllegalArgumentException("Par sem intenção em comum");
    }
    intencoes = Collections.unmodifiableSet(EnumSet.copyOf(intencoes));
  }

  public ParDeContas par() {
    return ParDeContas.de(a, b);
  }
}
