package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

class ConfiguracaoDosFornecedoresTest {

  private static final String CHAVE = "chave-de-acesso-de-teste";
  private static final String SEGREDO = "segredo-de-acesso-de-teste";
  private static final String ARN = "arn:aws:iam::123456789012:role/duora-captura";
  private static final URI URL_DIDIT = URI.create("https://verification.didit.me");

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
  void awsCompletaAceita() {
    assertThatCode(aws(CHAVE, SEGREDO, ARN)::exigirCompleta).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "${DUORA_DIDIT_API_KEY}"})
  void diditSemChaveRecusada(@Nullable String chave) {
    ConfiguracaoDidit didit = didit(URL_DIDIT, chave);

    assertThatThrownBy(didit::exigirCompleta)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.verificacao.didit.api-key")
        .hasMessageNotContaining("${");
  }

  @Test
  void diditSemHttpsRecusada() {
    ConfiguracaoDidit didit = didit(URI.create("http://verification.didit.me"), "chave-didit");

    assertThatThrownBy(didit::exigirCompleta)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("duora.verificacao.didit.url");
  }

  @Test
  void diditCompletaAceita() {
    assertThatCode(didit(URL_DIDIT, "chave-didit")::exigirCompleta).doesNotThrowAnyException();
  }

  @Test
  void toStringNaoMostraSegredos() {
    String textos =
        aws("chave-canario", "segredo-canario", ARN) + " " + didit(URL_DIDIT, "chave-canario");

    assertThat(textos)
        .doesNotContain("chave-canario", "segredo-canario", "123456789012")
        .contains("ConfiguracaoAws", "ConfiguracaoDidit", "PT4S", "PT9S");
  }

  @Test
  void padroesDoBinder() {
    Binder vazio = new Binder(new MapConfigurationPropertySource(Map.of()));

    ConfiguracaoAws aws =
        vazio.bindOrCreate("duora.verificacao.aws", Bindable.of(ConfiguracaoAws.class));
    ConfiguracaoDidit didit =
        vazio.bindOrCreate("duora.verificacao.didit", Bindable.of(ConfiguracaoDidit.class));

    assertThat(aws)
        .isEqualTo(
            new ConfiguracaoAws(
                null,
                null,
                null,
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(4)));
    assertThat(didit)
        .isEqualTo(
            new ConfiguracaoDidit(
                URL_DIDIT,
                null,
                30,
                Duration.ofSeconds(1),
                Duration.ofSeconds(9),
                Duration.ofSeconds(4)));
  }

  @Test
  void prodLigaOsFornecedoresReaisComAsChavesDoAmbiente() throws IOException {
    Properties prod =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));

    assertThat(prod)
        .containsEntry("duora.verificacao.provedor", "aws")
        .containsEntry("duora.verificacao.documento.provedor", "didit")
        .containsEntry(
            "duora.verificacao.aws.access-key-id", "${DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID}")
        .containsEntry(
            "duora.verificacao.aws.secret-access-key", "${DUORA_AWS_VERIFICACAO_SECRET_ACCESS_KEY}")
        .containsEntry("duora.verificacao.aws.papel-captura-arn", "${DUORA_AWS_PAPEL_CAPTURA_ARN}")
        .containsEntry("duora.verificacao.didit.api-key", "${DUORA_DIDIT_API_KEY}")
        .doesNotContainKey("duora.verificacao.documento.habilitado");
  }

  private static ConfiguracaoAws aws(
      @Nullable String chave, @Nullable String segredo, @Nullable String arn) {
    return new ConfiguracaoAws(
        chave, segredo, arn, Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  private static ConfiguracaoDidit didit(URI url, @Nullable String chave) {
    return new ConfiguracaoDidit(
        url, chave, 30, Duration.ofSeconds(1), Duration.ofSeconds(9), Duration.ofSeconds(4));
  }
}
