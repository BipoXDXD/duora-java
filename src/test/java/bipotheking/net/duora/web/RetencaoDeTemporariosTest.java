package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Retenção sobre um {@code @TempDir}, com o mtime dos temporários ajustado à mão. */
class RetencaoDeTemporariosTest {

  private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");

  @TempDir private Path raiz;

  private RetencaoDeTemporarios retencao() {
    return new RetencaoDeTemporarios(
        new ConfigDeImagens(
            raiz, "vips", "vipsheader", Duration.ofSeconds(15), Duration.ofSeconds(5), 2),
        Clock.fixed(AGORA, ZoneOffset.UTC));
  }

  private Path temporario(String nome, Duration idade) throws IOException {
    Path arquivo = Files.createDirectories(raiz.resolve("tmp")).resolve(nome);
    Files.writeString(arquivo, nome);
    Files.setLastModifiedTime(arquivo, FileTime.from(AGORA.minus(idade)));
    return arquivo;
  }

  @Test
  void nomeEPrazo() {
    RetencaoDeTemporarios retencao = retencao();

    assertThat(retencao.nome()).isEqualTo("imagens-temporarias");
    assertThat(retencao.prazo()).isEqualTo(Duration.parse("PT1H"));
  }

  @Test
  void apagaSoOVencido() throws IOException {
    RetencaoDeTemporarios retencao = retencao();
    Path vencido = temporario("vencido-n.webp", Duration.ofMinutes(61));
    Path recente = temporario("recente-n.webp", Duration.ofMinutes(59));

    int apagados = retencao.expirar(AGORA.minus(retencao.prazo()));

    assertThat(apagados).isOne();
    assertThat(vencido).doesNotExist();
    assertThat(recente).exists();
  }

  @Test
  void tamanhoContaSoOVencido() throws IOException {
    temporario("vencido-n.webp", Duration.ofMinutes(61));
    temporario("recente-n.webp", Duration.ofMinutes(59));

    assertThat(retencao().tamanho()).isOne();
  }

  @Test
  void bootEsvaziaTmp() throws IOException {
    Path recente = temporario("recente-n.webp", Duration.ZERO);
    Path vencido = temporario("vencido-n.webp", Duration.ofMinutes(61));

    retencao().esvaziarNoBoot();

    assertThat(recente).doesNotExist();
    assertThat(vencido).doesNotExist();
    assertThat(raiz.resolve("tmp")).isEmptyDirectory();
  }
}
