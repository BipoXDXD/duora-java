package bipotheking.net.duora.sala.infra;

import bipotheking.net.duora.sala.ConfigDoPareamento;
import java.time.Duration;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Monta o {@link ConfigDoPareamento} a partir de três prefixos ({@code duora.pareamento}, {@code
 * duora.fila} e {@code duora.encontros}). Propriedade ausente derruba o boot.
 */
@Configuration
class PareamentoConfig {

  @Bean
  ConfigDoPareamento configDoPareamento(Environment ambiente) {
    Binder binder = Binder.get(ambiente);
    return new ConfigDoPareamento(
        duracao(binder, "duora.pareamento.ciclo"),
        duracao(binder, "duora.pareamento.prazo-para-entrar"),
        duracao(binder, "duora.fila.ausencia-maxima"),
        inteiro(binder, "duora.encontros.maximo-simultaneos"),
        inteiro(binder, "duora.pareamento.lote"));
  }

  private static Duration duracao(Binder binder, String nome) {
    return binder.bind(nome, Duration.class).orElseThrow(() -> ausente(nome));
  }

  private static int inteiro(Binder binder, String nome) {
    return binder.bind(nome, Integer.class).orElseThrow(() -> ausente(nome));
  }

  private static IllegalStateException ausente(String nome) {
    return new IllegalStateException("Propriedade obrigatória ausente: " + nome);
  }
}
