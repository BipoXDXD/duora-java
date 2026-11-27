package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import java.io.IOException;
import java.time.Clock;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Em produção, o boot cai se algum falso da verificação foi registrado, qualquer que seja a grafia
 * do provedor que o ligou.
 */
class GuardaDaVerificacaoRealTest {

  private static final String PROVEDOR = "duora.verificacao.provedor";
  private static final String DOCUMENTO = "duora.verificacao.documento.provedor";
  private static final String MENSAGEM = "A verificação falsa não roda em produção";

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withUserConfiguration(
              GuardaDaVerificacaoReal.class,
              ProvaDeVidaFalsa.class,
              ConferenciaDeDocumentoFalsa.class)
          .withBean(RepositorioDeVerificacoes.class, () -> mock(RepositorioDeVerificacoes.class))
          .withBean(RepositorioDeContas.class, () -> mock(RepositorioDeContas.class))
          .withBean(Clock.class, Clock::systemUTC);

  private final ApplicationContextRunner producao =
      contexto.withPropertyValues("spring.profiles.active=prod");

  @Test
  void provaFalsaEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=falso", DOCUMENTO + "=didit")
        .run(GuardaDaVerificacaoRealTest::bootCaiuNaGuarda);
  }

  @Test
  void documentoFalsoEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=falso")
        .run(GuardaDaVerificacaoRealTest::bootCaiuNaGuarda);
  }

  @Test
  void provaFalsaEmMaiusculasEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=FALSO", DOCUMENTO + "=didit")
        .run(GuardaDaVerificacaoRealTest::bootCaiuNaGuarda);
  }

  @Test
  void documentoFalsoComInicialMaiusculaEmProdDerrubaOBoot() {
    producao
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=Falso")
        .run(GuardaDaVerificacaoRealTest::bootCaiuNaGuarda);
  }

  @Test
  void provedoresReaisPassam() {
    producao
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=didit")
        .run(aplicacao -> assertThat(aplicacao).hasSingleBean(GuardaDaVerificacaoReal.class));
  }

  @Test
  void foraDeProdAGuardaNaoEntra() {
    contexto
        .withPropertyValues(PROVEDOR + "=falso", DOCUMENTO + "=falso")
        .run(aplicacao ->
            assertThat(aplicacao)
                .hasNotFailed()
                .doesNotHaveBean(GuardaDaVerificacaoReal.class)
                .hasSingleBean(ProvaDeVidaFalsa.class));
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
        .run(GuardaDaVerificacaoRealTest::bootCaiuNaGuarda);
  }

  private static void bootCaiuNaGuarda(AssertableApplicationContext aplicacao) {
    assertThat(aplicacao)
        .getFailure()
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(MENSAGEM);
  }
}
