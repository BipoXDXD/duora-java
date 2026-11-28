package bipotheking.net.duora.web;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Processamento e armazenamento de imagens ({@code duora.imagens.*}).
 *
 * @param dir raiz dos arquivos; os temporários ficam em {@code <dir>/tmp}
 * @param vips executável do {@code vips} (≥ 8.15)
 * @param vipsheader executável do {@code vipsheader}
 * @param prazo tempo máximo do processamento de uma imagem, contado depois de conseguir a vaga
 * @param espera tempo máximo na fila por uma vaga
 * @param simultaneos quantas imagens são processadas ao mesmo tempo
 */
@ConfigurationProperties("duora.imagens")
public record ConfigDeImagens(
    Path dir,
    @DefaultValue("vips") String vips,
    @DefaultValue("vipsheader") String vipsheader,
    @DefaultValue("PT15S") Duration prazo,
    @DefaultValue("PT5S") Duration espera,
    @DefaultValue("2") int simultaneos) {

  public ConfigDeImagens {
    if (simultaneos < 1) {
      throw new IllegalArgumentException("simultaneos precisa ser pelo menos 1: " + simultaneos);
    }
    if (prazo.isNegative() || prazo.isZero()) {
      throw new IllegalArgumentException("prazo precisa ser positivo: " + prazo);
    }
    if (espera.isNegative()) {
      throw new IllegalArgumentException("espera não pode ser negativa: " + espera);
    }
  }
}
