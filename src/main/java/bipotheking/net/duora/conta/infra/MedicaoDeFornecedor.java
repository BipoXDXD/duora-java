package bipotheking.net.duora.conta.infra;

import io.micrometer.core.instrument.Meter.MeterProvider;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;

/**
 * Mede o tempo de cada chamada aos fornecedores da verificação de idade no Timer {@code
 * duora.verificacao.fornecedor}, com as tags {@code fornecedor}, {@code operacao} e {@code
 * desfecho} ({@code sucesso} ou {@code falha}). Não loga: a exceção volta intacta para quem chamou,
 * que decide o que registrar.
 */
final class MedicaoDeFornecedor {

  private static final String NOME = "duora.verificacao.fornecedor";

  private final MeterRegistry metricas;
  private final MeterProvider<Timer> timers;

  MedicaoDeFornecedor(MeterRegistry metricas) {
    this.metricas = metricas;
    this.timers =
        Timer.builder(NOME)
            .description("Duração das chamadas aos fornecedores da verificação de idade")
            .withRegistry(metricas);
  }

  <T> T medir(String fornecedor, String operacao, Supplier<T> chamada) {
    Timer.Sample amostra = Timer.start(metricas);
    T resultado;
    try {
      resultado = chamada.get();
    } catch (RuntimeException e) {
      amostra.stop(timer(fornecedor, operacao, "falha"));
      throw e;
    }
    amostra.stop(timer(fornecedor, operacao, "sucesso"));
    return resultado;
  }

  private Timer timer(String fornecedor, String operacao, String desfecho) {
    return timers.withTags("fornecedor", fornecedor, "operacao", operacao, "desfecho", desfecho);
  }
}
