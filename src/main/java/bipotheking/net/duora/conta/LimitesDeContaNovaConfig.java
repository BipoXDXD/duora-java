package bipotheking.net.duora.conta;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites diários da conta nova ({@code duora.conta-nova.*}). Limite menor que 1 derruba o boot:
 * com zero, a conta nova não faria nada nos primeiros 7 dias, sem aviso.
 */
@ConfigurationProperties("duora.conta-nova")
public record LimitesDeContaNovaConfig(
    @DefaultValue("200") int mensagensPorDia,
    @DefaultValue("10") int imagensPorDia,
    @DefaultValue("3") int convitesPorDia) {

  public LimitesDeContaNovaConfig {
    exigirPositivo(mensagensPorDia, "duora.conta-nova.mensagens-por-dia");
    exigirPositivo(imagensPorDia, "duora.conta-nova.imagens-por-dia");
    exigirPositivo(convitesPorDia, "duora.conta-nova.convites-por-dia");
  }

  private static void exigirPositivo(int valor, String propriedade) {
    if (valor < 1) {
      throw new IllegalArgumentException(propriedade + " precisa ser pelo menos 1: " + valor);
    }
  }
}
