package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Limites de tentativas de verificação em 24 h corridas ({@code duora.verificacao.*}). Limite menor
 * que 1 derruba o boot: com zero, ninguém conseguiria se verificar, sem aviso.
 */
@ConfigurationProperties("duora.verificacao")
public record LimitesDaVerificacao(
    @DefaultValue("3") int tentativasPorDia, @DefaultValue("2") int enviosDeDocumentoPorDia) {

  public LimitesDaVerificacao {
    exigirPositivo(tentativasPorDia, "duora.verificacao.tentativas-por-dia");
    exigirPositivo(enviosDeDocumentoPorDia, "duora.verificacao.envios-de-documento-por-dia");
  }

  /** O limite do dia para a finalidade da tentativa. */
  public int limite(FinalidadeDaTentativa finalidade) {
    return switch (finalidade) {
      case SELFIE -> tentativasPorDia;
      case DOCUMENTO -> enviosDeDocumentoPorDia;
    };
  }

  private static void exigirPositivo(int valor, String propriedade) {
    if (valor < 1) {
      throw new IllegalArgumentException(propriedade + " precisa ser pelo menos 1: " + valor);
    }
  }
}
