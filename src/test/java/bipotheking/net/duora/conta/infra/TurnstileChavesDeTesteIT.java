package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Fala com a Cloudflare de verdade usando as chaves de teste públicas dela. Fica fora do failsafe
 * padrão: roda só com {@code -Dit.test=TurnstileChavesDeTesteIT}, e uma vez por execução (só no
 * chromium).
 */
class TurnstileChavesDeTesteIT {

  private static final URI URL =
      URI.create("https://challenges.cloudflare.com/turnstile/v0/siteverify");
  private static final String TOKEN_FALSO = "XXXX.DUMMY.TOKEN.XXXX";
  private static final String IP = "203.0.113.7";

  @BeforeAll
  static void soNoChromium() {
    Assumptions.assumeTrue("chromium".equals(System.getProperty("navegador", "chromium")));
  }

  /** Monta como a produção: builder do Boot (Jackson estrito) passando por {@code comTimeouts}. */
  private static boolean verificarCom(String segredo) {
    AtomicBoolean resultado = new AtomicBoolean();
    TurnstileComBuilderDoBootTest.CONTEXTO.run(contexto ->
        resultado.set(
            TurnstileVerificacao.comTimeouts(
                    contexto.getBean(RestClient.Builder.class),
                    new TurnstileConfig("1x00000000000000000000AA", segredo, URL))
                .verificar(TOKEN_FALSO, IP)));
    return resultado.get();
  }

  @Test
  void segredoQueSempreAceitaDevolveTrue() {
    assertThat(verificarCom("1x0000000000000000000000000000000AA")).isTrue();
  }

  @Test
  void segredoQueSempreRecusaDevolveFalse() {
    assertThat(verificarCom("2x0000000000000000000000000000000AA")).isFalse();
  }
}
