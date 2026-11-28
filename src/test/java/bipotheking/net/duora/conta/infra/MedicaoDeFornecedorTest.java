package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class MedicaoDeFornecedorTest {

  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();
  private final MedicaoDeFornecedor medicao = new MedicaoDeFornecedor(metricas);

  @Test
  void sucessoContaComDesfechoSucesso() {
    int valor = medicao.medir("aws", "resultado", () -> 7);

    assertThat(valor).isEqualTo(7);
    Timer sucesso = timer("aws", "resultado", "sucesso");
    assertThat(sucesso).isNotNull();
    assertThat(sucesso.count()).isEqualTo(1);
  }

  @Test
  void falhaContaERelancaAMesmaExcecao() {
    IllegalStateException e = new IllegalStateException("fora do ar");

    Throwable lancada =
        catchThrowable(() ->
            medicao.medir(
                "aws",
                "documento-frente",
                () -> {
                  throw e;
                }));

    assertThat(lancada).isSameAs(e);
    Timer falha = timer("aws", "documento-frente", "falha");
    assertThat(falha).isNotNull();
    assertThat(falha.count()).isEqualTo(1);
    assertThat(timer("aws", "documento-frente", "sucesso")).isNull();
  }

  private @Nullable Timer timer(String fornecedor, String operacao, String desfecho) {
    return metricas
        .find("duora.verificacao.fornecedor")
        .tags("fornecedor", fornecedor, "operacao", operacao, "desfecho", desfecho)
        .timer();
  }
}
