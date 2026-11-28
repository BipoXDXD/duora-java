package bipotheking.net.duora.web;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Processador com o libvips de verdade (≥ 8.15 no PATH). As entradas sintéticas e os {@code vips}
 * falsos nascem no {@code @TempDir}: o repositório guarda só as fixtures pequenas de {@code
 * src/test/resources/imagens}.
 */
class ProcessadorDeImagemVipsTest {

  private static final Duration PRAZO = Duration.ofSeconds(15);
  private static final Duration ESPERA = Duration.ofSeconds(5);
  private static final String RETRATO = "retrato-exif-orientacao-6.jpg";

  @TempDir private Path raiz;

  @Test
  void jpegViraWebpSemMetadados() throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(RETRATO))) {
      for (Path saida : List.of(imagem.nitida(), imagem.borrada())) {
        assertThat(ehWebp(saida)).as("%s é WebP", saida.getFileName()).isTrue();
        assertThat(vips("vipsheader", "-a", saida.toString()))
            .doesNotContain("exif-data", "xmp-data", "icc-profile-data");
      }
    }
  }

  @Test
  void orientacaoExifAplicada() throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(RETRATO))) {
      assertThat(dimensoes(imagem.nitida())).containsExactly(800, 1200);
      assertThat(new int[] {imagem.largura(), imagem.altura()}).containsExactly(800, 1200);
    }
  }

  @Test
  void maiorLadoLimitadoA1600() throws Exception {
    try (ImagemProcessada imagem = processador().processar(preta(3200, 1600))) {
      assertThat(dimensoes(imagem.nitida())).containsExactly(1600, 800);
    }
  }

  @Test
  void imagemPequenaNaoAmpliada() throws Exception {
    try (ImagemProcessada imagem = processador().processar(preta(400, 300))) {
      assertThat(dimensoes(imagem.nitida())).containsExactly(400, 300);
    }
  }

  /**
   * A borrada passa por 32 px: o retrato 800 × 1200 vira 21 × 32 e, × 12,5, 262,5 × 400. O menor
   * lado sai 262 ou 263 conforme o arredondamento da versão do libvips.
   */
  @Test
  void borradaTemMaiorLado400() throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(RETRATO))) {
      int[] lados = dimensoes(imagem.borrada());
      assertThat(lados[1]).isEqualTo(400);
      assertThat(lados[0]).isBetween(262, 263);
    }
  }

  @Test
  void borradaNaoGuardaDetalhe() throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(RETRATO))) {
      double nitida = desvioPadrao(imagem.nitida());
      double borrada = desvioPadrao(imagem.borrada());
      assertThat(borrada).isLessThan(nitida * 0.25);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"paisagem.png", "quadrado.webp"})
  void pngEWebpAceitos(String nome) throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(nome))) {
      assertThat(ehWebp(imagem.nitida())).isTrue();
      assertThat(ehWebp(imagem.borrada())).isTrue();
    }
  }

  @Test
  void textoComExtensaoJpgRecusado() {
    ErroDeNegocio erro = recusa(() -> processador().processar(fixture("texto.txt")));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.FORMATO_NAO_SUPORTADO);
    assertThat(erro.getMessage()).isEqualTo("Envie uma foto em JPEG, PNG ou WebP.");
  }

  @Test
  void dimensaoGiganteRecusada() throws Exception {
    InputStream gigante = pngSoComCabecalho(30_000, 30_000);
    long inicio = System.nanoTime();

    ErroDeNegocio erro = recusa(() -> processador().processar(gigante));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.IMAGEM_INVALIDA);
    assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(2));
  }

  @Test
  void areaAcimaDe40MegapixelsRecusada() throws Exception {
    ErroDeNegocio erro = recusa(() -> processador().processar(pngSoComCabecalho(8_000, 5_001)));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.IMAGEM_INVALIDA);
  }

  @Test
  void arquivoTruncadoRecusado() throws Exception {
    byte[] inicio = Arrays.copyOf(bytes(RETRATO), 2_048);

    ErroDeNegocio erro = recusa(() -> processador().processar(new ByteArrayInputStream(inicio)));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.IMAGEM_INVALIDA);
  }

  @Test
  void seisMegabytesRecusados() {
    byte[] grande = new byte[6_291_456];
    grande[0] = (byte) 0xFF;
    grande[1] = (byte) 0xD8;
    grande[2] = (byte) 0xFF;

    ErroDeNegocio erro = recusa(() -> processador().processar(new ByteArrayInputStream(grande)));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.IMAGEM_GRANDE_DEMAIS);
  }

  @Test
  void processoLentoViraIndisponivel() throws Exception {
    ProcessadorDeImagemVips processador =
        processador(Duration.ofSeconds(1), ESPERA, 2, vipsLento(raiz.resolve("iniciou")));
    long inicio = System.nanoTime();

    ErroDeNegocio erro = recusa(() -> processador.processar(fixture(RETRATO)));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.PROCESSAMENTO_INDISPONIVEL);
    assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(3));
    assertThat(sleepsVivos()).isZero();
  }

  @Test
  void filaCheiaViraIndisponivel() throws Exception {
    Path iniciou = raiz.resolve("iniciou");
    ProcessadorDeImagemVips processador =
        processador(Duration.ofSeconds(2), Duration.ofMillis(100), 1, vipsLento(iniciou));
    CompletableFuture<Void> primeira =
        CompletableFuture.runAsync(() -> recusa(() -> processador.processar(fixture(RETRATO))));
    esperarArquivo(iniciou);

    ErroDeNegocio erro = recusa(() -> processador.processar(fixture(RETRATO)));

    assertThat(erro.codigo()).isEqualTo(CodigoWeb.PROCESSAMENTO_INDISPONIVEL);
    primeira.get(5, TimeUnit.SECONDS);
  }

  @Test
  void temporariosApagados() throws Exception {
    try (ImagemProcessada imagem = processador().processar(fixture(RETRATO))) {
      assertThat(arquivosEm(raiz.resolve("tmp")))
          .containsExactlyInAnyOrder(imagem.nitida(), imagem.borrada());
    }

    assertThat(arquivosEm(raiz.resolve("tmp"))).isEmpty();
  }

  @Test
  void temporariosApagadosNaRecusa() throws Exception {
    byte[] inicio = Arrays.copyOf(bytes(RETRATO), 2_048);

    recusa(() -> processador().processar(new ByteArrayInputStream(inicio)));

    assertThat(arquivosEm(raiz.resolve("tmp"))).isEmpty();
  }

  @Test
  void versaoAntigaDerrubaOBoot() throws Exception {
    Path antigo = vipsFalso("vips-antigo", "8.14.0", "exit 1\n");

    assertThatThrownBy(() -> processador(PRAZO, ESPERA, 2, antigo.toString()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("8.15");
  }

  private ProcessadorDeImagemVips processador() {
    return processador(PRAZO, ESPERA, 2, "vips");
  }

  private ProcessadorDeImagemVips processador(
      Duration prazo, Duration espera, int simultaneos, String vips) {
    ConfigDeImagens config =
        new ConfigDeImagens(raiz, vips, "vipsheader", prazo, espera, simultaneos);
    return new ProcessadorDeImagemVips(config, new SimpleMeterRegistry());
  }

  /**
   * Responde ao {@code --version} na hora e dorme no resto ({@code exec}: o kill acerta o sleep).
   */
  private String vipsLento(Path iniciou) throws IOException {
    return vipsFalso("vips-lento", "8.15.1", "touch '" + iniciou + "'\nexec sleep 30\n").toString();
  }

  private Path vipsFalso(String nome, String versao, String resto) throws IOException {
    Path script = raiz.resolve(nome);
    String corpo =
        "#!/bin/sh\n"
            + "if [ \"$1\" = \"--version\" ]; then echo \"vips-"
            + versao
            + "\"; exit 0; fi\n"
            + resto;
    Files.writeString(script, corpo, US_ASCII);
    Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
    return script;
  }

  private static InputStream fixture(String nome) {
    InputStream entrada = ProcessadorDeImagemVipsTest.class.getResourceAsStream("/imagens/" + nome);
    assertThat(entrada).as("fixture %s", nome).isNotNull();
    return entrada;
  }

  private static byte[] bytes(String nome) throws IOException {
    try (InputStream entrada = fixture(nome)) {
      return entrada.readAllBytes();
    }
  }

  private InputStream preta(int largura, int altura) throws Exception {
    Path png = raiz.resolve("entradas").resolve(largura + "x" + altura + ".png");
    Files.createDirectories(png.getParent());
    vips("vips", "black", png.toString(), String.valueOf(largura), String.valueOf(altura));
    return Files.newInputStream(png);
  }

  /**
   * PNG com assinatura e IHDR válidos e nenhum dado de imagem: o {@code vipsheader} lê as dimensões
   * do cabeçalho, e o processador recusa antes de decodificar.
   */
  private static InputStream pngSoComCabecalho(int largura, int altura) {
    ByteBuffer ihdr = ByteBuffer.allocate(17);
    ihdr.put("IHDR".getBytes(US_ASCII)).putInt(largura).putInt(altura);
    ihdr.put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
    CRC32 crc = new CRC32();
    crc.update(ihdr.array());
    ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
    png.put(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
    png.putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
    return new ByteArrayInputStream(png.array());
  }

  private static ErroDeNegocio recusa(ThrowingRunnable acao) {
    ErroDeNegocio erro = catchThrowableOfType(ErroDeNegocio.class, () -> acao.run());
    assertThat(erro).as("recusa com ErroDeNegocio").isNotNull();
    return erro;
  }

  private static boolean ehWebp(Path arquivo) throws IOException {
    byte[] cabecalho;
    try (InputStream entrada = Files.newInputStream(arquivo)) {
      cabecalho = entrada.readNBytes(12);
    }
    return new String(cabecalho, 0, 4, US_ASCII).equals("RIFF")
        && new String(cabecalho, 8, 4, US_ASCII).equals("WEBP");
  }

  private static int[] dimensoes(Path arquivo) throws Exception {
    return new int[] {
      Integer.parseInt(vips("vipsheader", "-f", "width", arquivo.toString())),
      Integer.parseInt(vips("vipsheader", "-f", "height", arquivo.toString()))
    };
  }

  private static double desvioPadrao(Path arquivo) throws Exception {
    return Double.parseDouble(vips("vips", "deviate", arquivo.toString()));
  }

  private static String vips(String... argumentos) throws Exception {
    Process processo =
        new ProcessBuilder(argumentos).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    String saida = new String(processo.getInputStream().readAllBytes(), US_ASCII).trim();
    assertThat(processo.waitFor()).as(String.join(" ", argumentos)).isZero();
    return saida;
  }

  private static List<Path> arquivosEm(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      return List.of();
    }
    try (var arquivos = Files.list(dir)) {
      return arquivos.toList();
    }
  }

  private static long sleepsVivos() {
    return ProcessHandle.current()
        .descendants()
        .filter(ProcessHandle::isAlive)
        .filter(p -> p.info().command().map(c -> c.endsWith("sleep")).orElse(false))
        .count();
  }

  private static void esperarArquivo(Path arquivo) throws InterruptedException {
    long limite = System.nanoTime() + Duration.ofSeconds(5).toNanos();
    while (!Files.exists(arquivo)) {
      assertThat(System.nanoTime()).as("%s não apareceu", arquivo).isLessThan(limite);
      Thread.sleep(20);
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
