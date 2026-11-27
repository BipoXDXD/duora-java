package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.DocumentosLegais;
import bipotheking.net.duora.conta.EncarregadoDeDados;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

/** Carrega os documentos legais no boot; um arquivo inválido impede a subida. */
@Configuration
class LegalConfig {

  @Bean
  DocumentosLegais documentosLegais(
      ResourceLoader carregador,
      @Value("${duora.legal.dir:classpath:legal/}") String diretorio,
      @Value("${duora.contato.email}") String contato,
      EncarregadoDeDados encarregado) {
    return new DocumentosLegais(carregador, diretorio, contato, encarregado);
  }
}
