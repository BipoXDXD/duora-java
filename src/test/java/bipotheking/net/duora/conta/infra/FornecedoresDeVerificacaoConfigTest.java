package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class FornecedoresDeVerificacaoConfigTest {

  private static final ApplicationContextRunner CONTEXTO =
      new ApplicationContextRunner()
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withUserConfiguration(FornecedoresDeVerificacaoConfig.class);

  @Test
  void medicaoSempreRegistrada() {
    CONTEXTO.run(contexto -> assertThat(contexto).hasSingleBean(MedicaoDeFornecedor.class));
  }

  @Test
  void configuracoesSemChavesSobemSemExigir() {
    CONTEXTO.run(contexto ->
        assertThat(contexto)
            .hasSingleBean(ConfiguracaoAws.class)
            .hasSingleBean(ConfiguracaoDidit.class));
  }
}
