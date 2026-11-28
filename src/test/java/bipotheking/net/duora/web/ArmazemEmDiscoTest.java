package bipotheking.net.duora.web;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;

/** Armazém sobre um {@code @TempDir}: as imagens processadas são arquivos falsos em {@code tmp}. */
class ArmazemEmDiscoTest {

  private static final String CATEGORIA = "perfil";
  private static final Instant AGORA = Instant.parse("2026-11-22T12:00:00Z");

  @TempDir private Path raiz;

  private ArmazemEmDisco armazem() {
    return new ArmazemEmDisco(
        new ConfigDeImagens(
            raiz, "vips", "vipsheader", Duration.ofSeconds(15), Duration.ofSeconds(5), 2));
  }

  private ImagemProcessada imagem(String conteudo) throws IOException {
    Path tmp = Files.createDirectories(raiz.resolve("tmp"));
    Path nitida = Files.writeString(Files.createTempFile(tmp, "n", ".webp"), conteudo + "-n");
    Path borrada = Files.writeString(Files.createTempFile(tmp, "b", ".webp"), conteudo + "-b");
    Path paraModeracao = Files.writeString(Files.createTempFile(tmp, "m", ".jpg"), conteudo + "-m");
    return new ImagemProcessada(nitida, borrada, paraModeracao, 10, 10);
  }

  private void guardar(ArmazemEmDisco armazem, UUID id) throws IOException {
    try (ImagemProcessada imagem = imagem(id.toString())) {
      armazem.guardar(CATEGORIA, id, imagem);
    }
  }

  private static String conteudo(Resource recurso) throws IOException {
    try (InputStream entrada = recurso.getInputStream()) {
      return new String(entrada.readAllBytes(), US_ASCII);
    }
  }

  private void guardarCom(ArmazemEmDisco armazem, UUID id, Instant mtime) throws IOException {
    guardar(armazem, id);
    String pasta = id.toString().substring(34);
    for (Variante variante : Variante.values()) {
      Path arquivo =
          raiz.resolve(CATEGORIA).resolve(pasta).resolve(id + "-" + variante.sufixo() + ".webp");
      Files.setLastModifiedTime(arquivo, FileTime.from(mtime));
    }
  }

  @Test
  void guardarELer() throws IOException {
    ArmazemEmDisco armazem = armazem();
    UUID id = UUID.randomUUID();

    guardar(armazem, id);

    assertThat(conteudo(armazem.ler(CATEGORIA, id, Variante.NITIDA).orElseThrow()))
        .isEqualTo(id + "-n");
    assertThat(conteudo(armazem.ler(CATEGORIA, id, Variante.BORRADA).orElseThrow()))
        .isEqualTo(id + "-b");
  }

  @Test
  void subpastaPelosUltimosHex() throws IOException {
    UUID id = UUID.fromString("0f3c2a1e-9b7d-4c55-8e21-6a4d3f0c71ab");

    guardar(armazem(), id);

    assertThat(raiz.resolve("perfil/ab/" + id + "-nitida.webp")).isRegularFile();
    assertThat(raiz.resolve("perfil/ab/" + id + "-borrada.webp")).isRegularFile();
  }

  @Test
  void arquivoSoLegivelPeloDono() throws IOException {
    UUID id = UUID.fromString("0f3c2a1e-9b7d-4c55-8e21-6a4d3f0c71ab");

    guardar(armazem(), id);

    for (String sufixo : new String[] {"nitida", "borrada"}) {
      Path arquivo = raiz.resolve("perfil/ab/" + id + "-" + sufixo + ".webp");
      assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(arquivo)))
          .isEqualTo("rw-------");
    }
    for (String pasta : new String[] {"perfil", "perfil/ab"}) {
      assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(raiz.resolve(pasta))))
          .as("pasta %s", pasta)
          .isEqualTo("rwx------");
    }
  }

  @Test
  void apagarRemoveAsDuas() throws IOException {
    ArmazemEmDisco armazem = armazem();
    UUID id = UUID.randomUUID();
    guardar(armazem, id);

    armazem.apagar(CATEGORIA, id);

    assertThat(armazem.ler(CATEGORIA, id, Variante.NITIDA)).isEmpty();
    assertThat(armazem.ler(CATEGORIA, id, Variante.BORRADA)).isEmpty();
  }

  @Test
  void apagarAusenteNaoFalha() {
    ArmazemEmDisco armazem = armazem();

    armazem.apagar(CATEGORIA, UUID.randomUUID());

    assertThat(raiz.resolve(CATEGORIA)).doesNotExist();
  }

  @Test
  void lerAusenteVazio() {
    Optional<Resource> lida = armazem().ler(CATEGORIA, UUID.randomUUID(), Variante.NITIDA);

    assertThat(lida).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"../x", "Perfil", ""})
  void categoriaInvalidaRecusada(String categoria) {
    ArmazemEmDisco armazem = armazem();

    assertThatThrownBy(() -> armazem.ler(categoria, UUID.randomUUID(), Variante.NITIDA))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void guardadasAntesDeFiltraPorMtime() throws IOException {
    ArmazemEmDisco armazem = armazem();
    UUID antiga = UUID.randomUUID();
    UUID recente = UUID.randomUUID();
    guardarCom(armazem, antiga, AGORA.minus(Duration.ofDays(2)));
    guardarCom(armazem, recente, AGORA);

    assertThat(armazem.guardadasAntesDe(CATEGORIA, AGORA.minus(Duration.ofDays(1)), null, 10))
        .containsExactly(antiga);
  }

  @Test
  void guardadasAntesDePaginaPeloCursor() throws IOException {
    ArmazemEmDisco armazem = armazem();
    UUID primeira = UUID.fromString("10000000-0000-4000-8000-0000000000aa");
    UUID segunda = UUID.fromString("20000000-0000-4000-8000-0000000000bb");
    UUID terceira = UUID.fromString("f0000000-0000-4000-8000-0000000000cc");
    Instant antes = AGORA.minus(Duration.ofDays(2));
    for (UUID id : new UUID[] {terceira, primeira, segunda}) {
      guardarCom(armazem, id, antes);
    }

    assertThat(armazem.guardadasAntesDe(CATEGORIA, AGORA, null, 2))
        .containsExactly(primeira, segunda);
    assertThat(armazem.guardadasAntesDe(CATEGORIA, AGORA, segunda, 2)).containsExactly(terceira);
    assertThat(armazem.guardadasAntesDe(CATEGORIA, AGORA, terceira, 2)).isEmpty();
  }
}
