package bipotheking.net.duora.push.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.interaso.webpush.VapidKeys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Teste manual: gera um par VAPID novo e o imprime na saída, nunca em arquivo. Fica fora do {@code
 * verify} pelo nome (o Surefire só pega {@code *Test}) e pela propriedade. Rode com {@code ./mvnw
 * test -Dtest=GerarChavesVapid -Dvapid.gerar=sim -DenableOutErrElements=false} (a última opção tira
 * a saída do relatório XML em {@code target/}) e copie as duas linhas para as variáveis do Coolify.
 */
class GerarChavesVapid {

  @Test
  @EnabledIfSystemProperty(named = "vapid.gerar", matches = "sim")
  void gerarParVapid() {
    VapidKeys par = VapidKeys.generate();
    String publica = WebPushInterasoAdapterTest.base64(par.getApplicationServerKey());
    String privada =
        WebPushInterasoAdapterTest.base64(WebPushInterasoAdapterTest.escalar(par.getPrivateKey()));

    VapidKeys relido = VapidKeys.fromUncompressedBytes(publica, privada);
    assertThat(relido.getApplicationServerKey()).isEqualTo(par.getApplicationServerKey());

    System.out.println("DUORA_VAPID_PUBLICA=" + publica);
    System.out.println("DUORA_VAPID_PRIVADA=" + privada);
  }
}
