package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.CredencialDoNavegador;
import bipotheking.net.duora.conta.dominio.ProvaDeVida;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Os beans reais da verificação só sobem com o provedor real e as chaves completas. Nenhum teste
 * chama a AWS: montar os clientes não abre conexão. O contexto não enxerga as variáveis de
 * ambiente, para que uma {@code DUORA_*} exportada na máquina não resolva os placeholders.
 */
class FornecedoresDeVerificacaoConfigTest {

  private static final ApplicationContextRunner CONTEXTO =
      new ApplicationContextRunner()
          .withInitializer(contexto ->
              contexto
                  .getEnvironment()
                  .getPropertySources()
                  .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME))
          .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
          .withUserConfiguration(FornecedoresDeVerificacaoConfig.class);

  private static final String[] AWS_FICTICIA = {
    "duora.verificacao.provedor=aws",
    "duora.verificacao.aws.access-key-id=chave-de-acesso-ficticia",
    "duora.verificacao.aws.secret-access-key=segredo-de-acesso-ficticio",
    "duora.verificacao.aws.papel-captura-arn=arn:aws:iam::123456789012:role/duora-captura"
  };

  @Test
  void medicaoSempreRegistrada() {
    CONTEXTO.run(contexto -> assertThat(contexto).hasSingleBean(MedicaoDeFornecedor.class));
  }

  @Test
  void configuracoesSemChavesSobemSemExigir() {
    CONTEXTO.run(contexto -> assertThat(contexto).hasSingleBean(ConfiguracaoAws.class));
  }

  @Test
  void falsoNaoCriaBeansReais() {
    CONTEXTO
        .withPropertyValues(
            "duora.verificacao.provedor=falso", "duora.verificacao.documento.provedor=falso")
        .run(contexto ->
            assertThat(contexto)
                .hasNotFailed()
                .doesNotHaveBean(ProvaDeVidaAws.class)
                .doesNotHaveBean(ConferenciaDeDocumentoAws.class));
  }

  @Test
  void awsCriaProvaDeVidaECredencial() {
    CONTEXTO
        .withPropertyValues(AWS_FICTICIA)
        .run(contexto -> {
          assertThat(contexto)
              .hasSingleBean(ProvaDeVidaAws.class)
              .hasSingleBean(ProvaDeVida.class)
              .hasSingleBean(CredencialDoNavegador.class);
          assertThat(contexto.getBean(CredencialDoNavegador.class))
              .isSameAs(contexto.getBean(ProvaDeVida.class));
        });
  }

  /** A conferência usa só a credencial do back; o ARN do papel de captura é da prova de vida. */
  @Test
  void awsCriaConferenciaSemOArnDoPapel() {
    CONTEXTO
        .withPropertyValues(
            "duora.verificacao.documento.provedor=aws",
            "duora.verificacao.aws.access-key-id=chave-de-acesso-ficticia",
            "duora.verificacao.aws.secret-access-key=segredo-de-acesso-ficticio")
        .run(contexto ->
            assertThat(contexto)
                .hasSingleBean(ConferenciaDeDocumentoAws.class)
                .hasSingleBean(ConferenciaDeDocumento.class)
                .doesNotHaveBean(ProvaDeVidaAws.class));
  }

  @Test
  void awsSemChaveDerrubaOContexto() {
    CONTEXTO
        .withPropertyValues("duora.verificacao.provedor=aws")
        .run(contexto ->
            assertThat(contexto.getStartupFailure())
                .rootCause()
                .hasMessageContaining("duora.verificacao.aws.access-key-id"));
  }

  @Test
  void conferenciaSemChaveDerrubaOContexto() {
    CONTEXTO
        .withPropertyValues(
            "duora.verificacao.documento.provedor=aws",
            "duora.verificacao.aws.access-key-id=${DUORA_AWS_VERIFICACAO_ACCESS_KEY_ID}")
        .run(contexto ->
            assertThat(contexto.getStartupFailure())
                .rootCause()
                .hasMessageContaining("duora.verificacao.aws.access-key-id"));
  }

  @Test
  void falhaDaAwsNaoTrazAChave() {
    String chave = "chave-de-acesso-ficticia-visivel";
    CONTEXTO
        .withPropertyValues(
            "duora.verificacao.provedor=aws", "duora.verificacao.aws.access-key-id=" + chave)
        .run(contexto -> {
          Throwable falha = contexto.getStartupFailure();
          assertThat(falha)
              .rootCause()
              .hasMessageContaining("duora.verificacao.aws.secret-access-key");
          for (Throwable causa = falha; causa != null; causa = causa.getCause()) {
            assertThat(causa.toString()).doesNotContain(chave);
          }
        });
  }

  @Test
  void prodSemVariaveisDerrubaOContexto() throws IOException {
    Properties prod =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application-prod.properties"));
    String[] verificacao =
        prod.stringPropertyNames().stream()
            .filter(chave -> chave.startsWith("duora.verificacao."))
            .map(chave -> chave + "=" + prod.getProperty(chave))
            .toArray(String[]::new);

    CONTEXTO
        .withPropertyValues(verificacao)
        .run(contexto ->
            assertThat(contexto.getStartupFailure())
                .rootCause()
                .hasMessageContaining("duora.verificacao.aws"));
  }
}
