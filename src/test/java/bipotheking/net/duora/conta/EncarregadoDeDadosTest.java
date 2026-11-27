package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class EncarregadoDeDadosTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(ComEncarregado.class);

  @Test
  void semEmailOContextoFalha() {
    contexto
        .withPropertyValues("duora.encarregado.nome=Pessoa encarregada")
        .run(ctx ->
            assertThat(ctx)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.encarregado.email"));
  }

  @Test
  void emailMalFormadoOContextoFalha() {
    contexto
        .withPropertyValues(
            "duora.encarregado.nome=Pessoa encarregada", "duora.encarregado.email=sem-arroba")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void comEmailOContextoSobe() {
    contexto
        .withPropertyValues(
            "duora.encarregado.nome=Pessoa encarregada",
            "duora.encarregado.email=dpo@exemplo.invalid")
        .run(ctx ->
            assertThat(ctx.getBean(EncarregadoDeDados.class))
                .isEqualTo(new EncarregadoDeDados("Pessoa encarregada", "dpo@exemplo.invalid")));
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "${DUORA_EMAIL_ENCARREGADO}"})
  void emailAusenteOuMarcadorNaoResolvidoERecusado(String email) {
    assertThatThrownBy(() -> new EncarregadoDeDados("Pessoa encarregada", email))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.encarregado.email");
  }

  @Test
  void prodExigeAVariavel() throws IOException {
    try (InputStream entrada =
        EncarregadoDeDadosTest.class.getResourceAsStream("/application-prod.properties")) {
      assertThat(entrada).as("application-prod.properties no classpath").isNotNull();
      String conteudo = new String(entrada.readAllBytes(), StandardCharsets.UTF_8);

      assertThat(conteudo.lines()).contains("duora.encarregado.email=${DUORA_EMAIL_ENCARREGADO}");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(EncarregadoDeDados.class)
  static class ComEncarregado {}
}
