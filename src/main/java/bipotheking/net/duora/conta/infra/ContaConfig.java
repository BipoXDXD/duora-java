package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.ResumoDeEmail;
import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.DominiosDescartaveis;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.client.RestClient;

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

  /**
   * A consulta é o repositório visto de fora: a conta inteira reduzida ao {@link ContaResumo}. Fica
   * fora do {@code ContasJdbc} porque as duas portas têm {@code porId(ContaId)} com retornos
   * diferentes, e uma classe Java não implementa as duas.
   */
  @Bean
  ConsultaDeContas consultaDeContas(RepositorioDeContas contas) {
    return id -> contas.porId(id).map(Conta::resumo);
  }

  @Bean
  VerificacaoHumana verificacaoHumana(RestClient.Builder builder, TurnstileConfig config) {
    return TurnstileVerificacao.comTimeouts(builder, config);
  }
}
