package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/** Em produção, o boot cai se algum fornecedor da verificação for o falso. */
class GuardaDaVerificacaoRealTest {

  private static final String PROVEDOR = "duora.verificacao.provedor";
  private static final String DOCUMENTO = "duora.verificacao.documento.provedor";

  private final ApplicationContextRunner producao =
      new ApplicationContextRunner()
          .withUserConfiguration(GuardaDaVerificacaoReal.class)
          .withPropertyValues("spring.profiles.active=prod");

  @Test
  void provaFalsaEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=falso", DOCUMENTO + "=didit")
        .run(aplicacao ->
            assertThat(aplicacao)
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A verificação falsa não roda em produção"));
  }

  @Test
  void documentoFalsoEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=falso")
        .run(aplicacao ->
            assertThat(aplicacao)
                .getFailure()
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("A verificação falsa não roda em produção"));
  }

  @Test
  void provedoresReaisPassam() {
    producao
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=didit")
        .run(aplicacao -> assertThat(aplicacao).hasSingleBean(GuardaDaVerificacaoReal.class));
  }

  @Test
  void perfilVerificacaoFalsaFixaOsDoisEmFalso() throws IOException {
    Properties perfil =
        PropertiesLoaderUtils.loadProperties(
            new ClassPathResource("application-verificacao-falsa.properties"));

    assertThat(perfil.getProperty(PROVEDOR)).isEqualTo("falso");
    assertThat(perfil.getProperty(DOCUMENTO)).isEqualTo("falso");
    producao
        .withPropertyValues(PROVEDOR + "=" + perfil.getProperty(PROVEDOR))
        .withPropertyValues(DOCUMENTO + "=" + perfil.getProperty(DOCUMENTO))
        .run(aplicacao ->
            assertThat(aplicacao)
                .getFailure()
                .rootCause()
                .hasMessage("A verificação falsa não roda em produção"));
  }
}
