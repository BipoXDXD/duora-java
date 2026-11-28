package bipotheking.net.duora.conta;

import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Registra os dublês programáveis da verificação de idade para quem a importa ({@code @Import}),
 * por cima dos falsos da V4 ({@code @Primary}): o padrão {@code provedor=falso} já registra beans
 * das mesmas portas. Só os testes de API da verificação a importam; o e2e e os demais testes seguem
 * com os falsos e os marcadores do e-mail. Cria um segundo contexto em cache, de propósito.
 */
@TestConfiguration(proxyBeanMethods = false)
public class DublesDeVerificacao {

  @Bean
  @Primary
  ProvaDeVidaDeTeste provaDeVidaDeTeste(Clock relogio) {
    return new ProvaDeVidaDeTeste(relogio);
  }

  @Bean
  @Primary
  ConferenciaDeDocumentoDeTeste conferenciaDeDocumentoDeTeste() {
    return new ConferenciaDeDocumentoDeTeste();
  }
}
