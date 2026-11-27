package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ResumoDeEmail;
import bipotheking.net.duora.conta.dominio.DominiosDescartaveis;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/** Monta as peças puras do domínio de conta a partir dos recursos e da configuração. */
@Configuration
class ContaConfig {

  @Bean
  DominiosDescartaveis dominiosDescartaveis(
      @Value("classpath:conta/dominios-descartaveis.txt") Resource lista) {
    try (BufferedReader leitor =
        new BufferedReader(new InputStreamReader(lista.getInputStream(), StandardCharsets.UTF_8))) {
      return new DominiosDescartaveis(leitor.lines().toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível ler a lista de domínios descartáveis", e);
    }
  }

  @Bean
  ResumoDeEmail resumoDeEmail(@Value("${duora.segredo-email}") String segredo) {
    return new ResumoDeEmail(segredo);
  }
}
