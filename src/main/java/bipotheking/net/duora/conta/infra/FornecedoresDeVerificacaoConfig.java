package bipotheking.net.duora.conta.infra;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fornecedores reais da verificação de idade. As configurações sobem sempre, sem exigir chaves; só
 * os beans reais (condicionais ao provedor) as exigem, e chave ausente derruba a subida. Os falsos
 * ({@code falso}) e os reais ({@code aws}) se excluem pelo valor da mesma propriedade.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConfiguracaoAws.class)
class FornecedoresDeVerificacaoConfig {

  @Bean
  MedicaoDeFornecedor medicaoDeFornecedor(MeterRegistry metricas) {
    return new MedicaoDeFornecedor(metricas);
  }

  /** Serve {@code ProvaDeVida} e {@code CredencialDoNavegador}; fecha os clientes ao parar. */
  @Bean
  @ConditionalOnProperty(name = "duora.verificacao.provedor", havingValue = "aws")
  ProvaDeVidaAws provaDeVidaAws(ConfiguracaoAws config, MedicaoDeFornecedor medicao) {
    String papelCapturaArn = config.exigirCompleta();
    return new ProvaDeVidaAws(
        ClientesAws.rekognition(config, Optional.empty()),
        ClientesAws.sts(config, Optional.empty()),
        papelCapturaArn,
        medicao);
  }

  /** Só a credencial do back; fecha os clientes ao parar. */
  @Bean
  @ConditionalOnProperty(name = "duora.verificacao.documento.provedor", havingValue = "aws")
  ConferenciaDeDocumentoAws conferenciaDeDocumentoAws(
      ConfiguracaoAws config, MedicaoDeFornecedor medicao) {
    return new ConferenciaDeDocumentoAws(
        ClientesAws.textract(config, Optional.empty()),
        ClientesAws.rekognition(config, Optional.empty()),
        medicao);
  }
}
