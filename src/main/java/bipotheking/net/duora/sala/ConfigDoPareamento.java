package bipotheking.net.duora.sala;

import java.time.Duration;
import java.util.Objects;

/**
 * Os números da fila e do pareamento, lidos de {@code duora.pareamento.*}, {@code
 * duora.fila.ausencia-maxima} e {@code duora.encontros.maximo-simultaneos}.
 *
 * @param ciclo de quanto em quanto tempo o pareamento roda
 * @param prazoParaEntrar quanto o par tem para chegar ao encontro
 * @param ausenciaMaxima quanto tempo sem a tela aberta tira a pessoa da fila
 * @param maximoSimultaneos quantos encontros ocupam vaga ao mesmo tempo
 * @param lote quantas entradas cada ciclo lê
 */
public record ConfigDoPareamento(
    Duration ciclo,
    Duration prazoParaEntrar,
    Duration ausenciaMaxima,
    int maximoSimultaneos,
    int lote) {

  public ConfigDoPareamento {
    positiva(ciclo, "ciclo");
    positiva(prazoParaEntrar, "prazoParaEntrar");
    positiva(ausenciaMaxima, "ausenciaMaxima");
    if (maximoSimultaneos < 1) {
      throw new IllegalArgumentException(
          "maximoSimultaneos precisa ser pelo menos 1: " + maximoSimultaneos);
    }
    if (lote < 2) {
      throw new IllegalArgumentException("lote precisa ser pelo menos 2: " + lote);
    }
  }

  private static void positiva(Duration duracao, String nome) {
    Objects.requireNonNull(duracao, nome);
    if (duracao.isNegative() || duracao.isZero()) {
      throw new IllegalArgumentException(nome + " precisa ser positivo: " + duracao);
    }
  }
}
