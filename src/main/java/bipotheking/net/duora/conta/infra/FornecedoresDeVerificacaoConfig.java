package bipotheking.net.duora.conta.infra;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fornecedores reais da verificação de idade. As configurações sobem sempre, sem exigir chaves; só
 * os beans reais (condicionais ao provedor) as exigem.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ConfiguracaoAws.class, ConfiguracaoDidit.class})
class FornecedoresDeVerificacaoConfig {

  @Bean
  MedicaoDeFornecedor medicaoDeFornecedor(MeterRegistry metricas) {
    return new MedicaoDeFornecedor(metricas);
  }
}
