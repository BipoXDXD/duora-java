package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class ConfiguracaoDosFornecedoresTest {

  private static final String CHAVE = "chave-de-acesso-de-teste";
  private static final String SEGREDO = "segredo-de-acesso-de-teste";
  private static final String ARN = "arn:aws:iam::123456789012:role/duora-captura";

  static Stream<Arguments> awsSemChave() {
    return Stream.of("access-key-id", "secret-access-key", "papel-captura-arn")
        .flatMap(propriedade ->
            Stream.of(null, "", "${DUORA_AWS_PAPEL_CAPTURA_ARN}")
                .map(valor -> Arguments.of(propriedade, valor)));
  }

  @ParameterizedTest
  @MethodSource("awsSemChave")
  void awsSemChaveRecusada(String propriedade, @Nullable String valor) {
    ConfiguracaoAws aws =
        aws(
            propriedade.equals("access-key-id") ? valor : CHAVE,
            propriedade.equals("secret-access-key") ? valor : SEGREDO,
            propriedade.equals("papel-captura-arn") ? valor : ARN);

    assertThatThrownBy(aws::exigirCompleta)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.verificacao.aws." + propriedade)
        .hasMessageNotContaining("${");
  }

  @Test
  void arnForaDoFormatoRecusado() {
    ConfiguracaoAws aws = aws(CHAVE, SEGREDO, "arn:aws:iam::123:role/x");

    assertThatThrownBy(aws::exigirCompleta)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.verificacao.aws.papel-captura-arn")
        .hasMessageNotContaining("arn:aws:iam::123");
  }

  @Test
  void awsCompletaDevolveOArnValidado() {
    assertThat(aws(CHAVE, SEGREDO, ARN).exigirCompleta()).isEqualTo(ARN);
  }

  @Test
  void toStringNaoMostraSegredos() {
    String texto = aws("chave-canario", "segredo-canario", ARN).toString();

    assertThat(texto)
        .doesNotContain("chave-canario", "segredo-canario", "123456789012")
        .contains("ConfiguracaoAws", "PT4S");
  }

  @Test
  void padroesDoBinder() {
    Binder vazio = new Binder(new MapConfigurationPropertySource(Map.of()));

    ConfiguracaoAws aws =
        vazio.bindOrCreate("duora.verificacao.aws", Bindable.of(ConfiguracaoAws.class));

    assertThat(aws)
        .isEqualTo(
            new ConfiguracaoAws(
                null,
                null,
                null,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(4)));
  }

  @Test
  void prodLigaOsFornecedoresReaisComAsChavesDoAmbiente() throws IOException {
    Properties prod =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

    assertThat(prod)
        .containsEntry("duora.verificacao.provedor", "aws")
        .containsEntry("duora.verificacao.documento.provedor", "aws")
        .containsEntry(
            "duora.verificacao.aws.access-key-id", "${DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID}")
        .containsEntry(
            "duora.verificacao.aws.secret-access-key", "${DUORA_AWS_VERIFICACAO_SECRET_ACCESS_KEY}")
        .containsEntry("duora.verificacao.aws.papel-captura-arn", "${DUORA_AWS_PAPEL_CAPTURA_ARN}")
        .doesNotContainKey("duora.verificacao.documento.habilitado")
        .doesNotContainKey("duora.verificacao.didit.api-key");
  }

  private static ConfiguracaoAws aws(
      @Nullable String chave, @Nullable String segredo, @Nullable String arn) {
    return new ConfiguracaoAws(
        chave, segredo, arn, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
  }
}
