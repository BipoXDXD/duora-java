package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * A config base não escolhe provedor da verificação: cada ambiente escolhe o seu, e sem escolha o
 * boot cai com uma mensagem que diz o que configurar, antes de qualquer bean faltar.
 */
class ProvedoresDaVerificacaoTest {

  private static final String PROVEDOR = "duora.verificacao.provedor";
  private static final String DOCUMENTO = "duora.verificacao.documento.provedor";

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner().withUserConfiguration(ProvedoresDaVerificacao.class);

  @Test
  void configBaseNaoEscolheProvedor() throws IOException {
    Properties base =
        PropertiesLoaderUtils.loadProperties(new ClassPathResource("application.properties"));

    assertThat(base).doesNotContainKeys(PROVEDOR, DOCUMENTO);
  }

  @Test
  void semProvaDeVidaOBootCaiComAChave() {
    contexto
        .withPropertyValues(DOCUMENTO + "=aws")
        .run(boot -> caiuCom(boot, PROVEDOR + " sem valor; use aws ou falso"));
  }

  @Test
  void semDocumentoOBootCaiComAChave() {
    contexto
        .withPropertyValues(PROVEDOR + "=aws")
        .run(boot -> caiuCom(boot, DOCUMENTO + " sem valor; use aws ou falso"));
  }

  @Test
  void provedorDesconhecidoDerrubaOBoot() {
    contexto
        .withPropertyValues(PROVEDOR + "=azure", DOCUMENTO + "=aws")
        .run(boot -> caiuCom(boot, PROVEDOR + "=azure não existe; use aws ou falso"));
  }

  @Test
  void provedoresReaisSobem() {
    contexto
        .withPropertyValues(PROVEDOR + "=aws", DOCUMENTO + "=aws")
        .run(boot -> assertThat(boot).hasNotFailed());
  }

  /** A condição que liga os adapters ignora maiúsculas; a checagem segue a mesma regra. */
  @Test
  void falsosEmMaiusculasSobem() {
    contexto
        .withPropertyValues(PROVEDOR + "=FALSO", DOCUMENTO + "=Falso")
        .run(boot -> assertThat(boot).hasNotFailed());
  }

  private static void caiuCom(AssertableApplicationContext boot, String mensagem) {
    assertThat(boot).hasFailed();
    assertThat(boot.getStartupFailure())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(mensagem);
  }
}
