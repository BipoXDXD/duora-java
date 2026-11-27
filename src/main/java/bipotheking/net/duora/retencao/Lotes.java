package bipotheking.net.duora.retencao;

import java.util.function.IntSupplier;

/**
 * O laço de lotes de toda {@link PoliticaDeRetencao}: a política fornece só o {@code DELETE} de um
 * lote de até 1000 linhas, numa transação própria ({@code TransactionTemplate}), e nenhuma repete o
 * laço.
 */
public final class Lotes {

  private Lotes() {}

  /**
   * Chama {@code lote} até ele devolver zero.
   *
   * @return a soma do que os lotes apagaram
   * @throws IllegalStateException se um lote devolve um número negativo
   */
  public static int apagarAteAcabar(IntSupplier lote) {
    int total = 0;
    int apagados;
    while ((apagados = lote.getAsInt()) != 0) {
      if (apagados < 0) {
        throw new IllegalStateException("Lote devolveu " + apagados + " itens apagados");
      }
      total += apagados;
    }
    return total;
  }
}
