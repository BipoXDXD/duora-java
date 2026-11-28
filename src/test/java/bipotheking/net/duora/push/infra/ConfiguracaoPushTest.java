package bipotheking.net.duora.push.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.push.EnvioDePush;
import com.interaso.webpush.VapidKeys;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Qual envio de push o contexto monta, e quando ele se recusa a subir. */
class ConfiguracaoPushTest {

  private final ApplicationContextRunner contexto =
      new ApplicationContextRunner()
          .withUserConfiguration(ComPush.class)
          .withPropertyValues(
              "duora.push.assunto=mailto:teste@teste.duora.invalid",
              "duora.push.hosts-permitidos=fcm.googleapis.com,push.apple.com");

  @Test
  void habilitadoSemChaveNaoSobe() {
    contexto
        .withPropertyValues(
            "duora.push.habilitado=true", "duora.push.chave-publica=", "duora.push.chave-privada=")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.push.chave-publica"));
  }

  @Test
  void habilitadoSemChavePrivadaNaoSobeNemCitaAChave() {
    String publica =
        WebPushInterasoAdapterTest.base64(VapidKeys.generate().getApplicationServerKey());
    contexto
        .withPropertyValues(
            "duora.push.habilitado=true",
            "duora.push.chave-publica=" + publica,
            "duora.push.chave-privada=${DUORA_VAPID_PRIVADA_INEXISTENTE}")
        .run(c ->
            assertThat(c)
                .hasFailed()
                .getFailure()
                .rootCause()
                .hasMessageContaining("duora.push.chave-privada")
                .hasMessageNotContaining("DUORA_VAPID_PRIVADA_INEXISTENTE"));
  }

  @Test
  void habilitadoComAsChavesUsaInteraso() {
    VapidKeys vapid = VapidKeys.generate();
    contexto
        .withPropertyValues(
            "duora.push.habilitado=true",
            "duora.push.chave-publica="
                + WebPushInterasoAdapterTest.base64(vapid.getApplicationServerKey()),
            "duora.push.chave-privada="
                + WebPushInterasoAdapterTest.base64(
                    WebPushInterasoAdapterTest.escalar(vapid.getPrivateKey())))
        .run(c ->
            assertThat(c).getBean(EnvioDePush.class).isInstanceOf(WebPushInterasoAdapter.class));
  }

  @Test
  void desligadoUsaOLog() {
    contexto
        .withPropertyValues("duora.push.habilitado=false")
        .run(c -> assertThat(c).getBean(EnvioDePush.class).isInstanceOf(EnvioDePushNoLog.class));
  }

  @Test
  void semAPropriedadeUsaOLog() {
    contexto.run(
        c -> assertThat(c).getBean(EnvioDePush.class).isInstanceOf(EnvioDePushNoLog.class));
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ConfiguracaoPush.class)
  @Import(PushConfig.class)
  static class ComPush {}
}
