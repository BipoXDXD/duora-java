package bipotheking.net.duora.pareamento;

import bipotheking.net.duora.conta.ContaId;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Duas contas diferentes, sem direção: {@code menor} vem antes de {@code maior} na ordem do {@code
 * uuid} do PostgreSQL (bytes sem sinal), nunca na de {@link UUID#compareTo}, que compara com sinal.
 */
public record ParDeContas(ContaId menor, ContaId maior) {

  /** A ordem do {@code uuid} do PostgreSQL: os 128 bits comparados sem sinal. */
  public static final Comparator<ContaId> ORDEM_SEM_SINAL =
      Comparator.comparing(
          ContaId::valor,
          Comparator.comparing(UUID::getMostSignificantBits, Long::compareUnsigned)
              .thenComparing(UUID::getLeastSignificantBits, Long::compareUnsigned));

  /**
   * @throws IllegalArgumentException se {@code menor} não vem antes de {@code maior}
   */
  public ParDeContas {
    Objects.requireNonNull(menor, "menor");
    Objects.requireNonNull(maior, "maior");
    if (ORDEM_SEM_SINAL.compare(menor, maior) >= 0) {
      throw new IllegalArgumentException("Par fora de ordem ou com a mesma conta");
    }
  }

  /**
   * O par das duas contas, em qualquer ordem.
   *
   * @throws IllegalArgumentException se as contas são a mesma
   */
  public static ParDeContas de(ContaId a, ContaId b) {
    return ORDEM_SEM_SINAL.compare(a, b) <= 0 ? new ParDeContas(a, b) : new ParDeContas(b, a);
  }

  public boolean contem(ContaId conta) {
    return menor.equals(conta) || maior.equals(conta);
  }

  /**
   * A outra conta do par.
   *
   * @throws IllegalArgumentException se {@code conta} não está no par
   */
  public ContaId outro(ContaId conta) {
    if (menor.equals(conta)) {
      return maior;
    }
    if (maior.equals(conta)) {
      return menor;
    }
    throw new IllegalArgumentException("Conta fora do par");
  }
}
