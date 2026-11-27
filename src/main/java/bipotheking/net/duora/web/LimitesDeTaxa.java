package bipotheking.net.duora.web;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Regras de limite de taxa, por nome ({@code duora.limites.regras.<nome>.capacidade|periodo}). O
 * multiplicador multiplica toda capacidade: o e2e o usa para folgar os limites sem desligá-los.
 */
@ConfigurationProperties("duora.limites")
public record LimitesDeTaxa(
    @DefaultValue Map<String, Regra> regras, @DefaultValue("1") int multiplicador) {

  public LimitesDeTaxa {
    if (multiplicador < 1) {
      throw new IllegalArgumentException(
          "multiplicador precisa ser pelo menos 1: " + multiplicador);
    }
    regras = Map.copyOf(regras);
  }

  /** Quantos pedidos cabem em cada período; o balde enche de novo inteiro ao fim do período. */
  public record Regra(int capacidade, Duration periodo) {

    public Regra {
      if (capacidade < 1) {
        throw new IllegalArgumentException("capacidade precisa ser pelo menos 1: " + capacidade);
      }
      if (periodo.isNegative() || periodo.isZero()) {
        throw new IllegalArgumentException("período precisa ser positivo: " + periodo);
      }
    }
  }
}
