package bipotheking.net.duora.web;

import static java.nio.charset.StandardCharsets.US_ASCII;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProcessadorDeImagem} com o libvips por processo (CLI): cada passo é um {@code vips} com
 * lista de argumentos, sem shell, com os carregadores não confiáveis bloqueados e um prazo que mata
 * o processo. Nítida: WebP q88 sem metadados, maior lado até 1600, orientação EXIF aplicada, sRGB.
 * Borrada: derivada de uma miniatura de 32 px ampliada a 400, para não guardar detalhe.
 *
 * <p>No boot exige libvips ≥ 8.15 (falha proposital).
 */
@Component
final class ProcessadorDeImagemVips implements ProcessadorDeImagem {

  static final long TAMANHO_MAXIMO = 5_242_880;
  static final int LADO_MAXIMO = 12_000;
  static final long AREA_MAXIMA = 40_000_000;
  static final String MENSAGEM_GRANDE_DEMAIS = "A imagem pode ter até 5 MB.";

  private static final Logger LOG = LoggerFactory.getLogger(ProcessadorDeImagemVips.class);
  private static final Pattern VERSAO = Pattern.compile("vips-(\\d+)\\.(\\d+)");
  private static final String VERSAO_EXIGIDA =
      "libvips 8.15 ou mais novo é necessário: brew install vips (Mac) ou apt-get install"
          + " libvips-tools";
  private static final String SAIDA_WEBP = "[Q=88,keep=none]";
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
  private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

  private final ConfigDeImagens config;
  private final Path tmp;
  private final Semaphore vagas;
  private final Timer ok;
  private final Timer recusada;
  private final Timer indisponivel;

  ProcessadorDeImagemVips(ConfigDeImagens config, MeterRegistry metricas) {
    this.config = config;
    this.tmp = config.dir().resolve("tmp");
    this.vagas = new Semaphore(config.simultaneos(), true);
    exigirVersaoSuportada();
    this.ok = timer(metricas, "ok");
    this.recusada = timer(metricas, "recusada");
    this.indisponivel = timer(metricas, "indisponivel");
    Gauge.builder("duora.imagens.fila", vagas, Semaphore::getQueueLength)
        .description("Imagens esperando vaga para o processamento")
        .register(metricas);
  }

  @Override
  public ImagemProcessada processar(InputStream arquivo) {
    long inicio = System.nanoTime();
    Timer resultado = indisponivel;
    try {
      ImagemProcessada imagem = processarNaVaga(arquivo);
      resultado = ok;
      return imagem;
    } catch (ErroDeNegocio erro) {
      resultado = erro.codigo() == CodigoWeb.PROCESSAMENTO_INDISPONIVEL ? indisponivel : recusada;
      throw erro;
    } finally {
      resultado.record(System.nanoTime() - inicio, TimeUnit.NANOSECONDS);
    }
  }

  private ImagemProcessada processarNaVaga(InputStream arquivo) {
    try {
      if (!vagas.tryAcquire(config.espera().toNanos(), TimeUnit.NANOSECONDS)) {
        throw indisponivel();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw indisponivel();
    }
    try {
      return processarAte(arquivo, System.nanoTime() + config.prazo().toNanos());
    } finally {
      vagas.release();
    }
  }

  private ImagemProcessada processarAte(InputStream arquivo, long prazoFinal) {
    String id = UUID.randomUUID().toString();
    Path entrada = tmp.resolve(id + ".entrada");
    Path nitida = tmp.resolve(id + "-n.webp");
    Path borrada = tmp.resolve(id + "-b.webp");
    // PNG e não .v: o VIPS_BLOCK_UNTRUSTED bloqueia o vipsload.
    Path miniatura = tmp.resolve(id + "-32.png");
    Path ampliada = tmp.resolve(id + "-400.png");
    boolean pronta = false;
    try {
      Files.createDirectories(tmp);
      copiarAteOLimite(arquivo, entrada);
      exigirFormatoAceito(entrada);
      exigirDimensoesAceitas(entrada, prazoFinal);
      exigirSucesso(
          executar(
              prazoFinal,
              config.vips(),
              "thumbnail",
              entrada.toString(),
              nitida + SAIDA_WEBP,
              "1600",
              "--size",
              "down",
              "--export-profile",
              "srgb",
              "--fail-on",
              "truncated"));
      derivar(prazoFinal, "thumbnail", nitida.toString(), miniatura.toString(), "32");
      derivar(
          prazoFinal,
          "resize",
          miniatura.toString(),
          ampliada.toString(),
          "12.5",
          "--kernel",
          "linear");
      derivar(prazoFinal, "gaussblur", ampliada.toString(), borrada + SAIDA_WEBP, "6");
      ImagemProcessada imagem =
          new ImagemProcessada(
              nitida,
              borrada,
              lado(nitida, "width", prazoFinal, false),
              lado(nitida, "height", prazoFinal, false));
      pronta = true;
      return imagem;
    } catch (IOException e) {
      LOG.warn("Falha de E/S ao processar uma imagem", e);
      throw indisponivel();
    } finally {
      apagar(entrada, miniatura, ampliada);
      if (!pronta) {
        apagar(nitida, borrada);
      }
    }
  }

  private static void copiarAteOLimite(InputStream arquivo, Path entrada) throws IOException {
    long total = 0;
    byte[] bloco = new byte[64 * 1024];
    try (OutputStream saida = Files.newOutputStream(entrada)) {
      int lidos;
      while ((lidos = arquivo.read(bloco)) != -1) {
        total += lidos;
        if (total > TAMANHO_MAXIMO) {
          throw new ErroDeNegocio(CodigoWeb.IMAGEM_GRANDE_DEMAIS, MENSAGEM_GRANDE_DEMAIS);
        }
        saida.write(bloco, 0, lidos);
      }
    }
  }

  private static void exigirFormatoAceito(Path entrada) throws IOException {
    byte[] cabecalho;
    try (InputStream leitura = Files.newInputStream(entrada)) {
      cabecalho = leitura.readNBytes(12);
    }
    boolean aceito =
        comeca(cabecalho, 0, JPEG)
            || comeca(cabecalho, 0, PNG)
            || (comeca(cabecalho, 0, RIFF) && comeca(cabecalho, 8, WEBP));
    if (!aceito) {
      throw new ErroDeNegocio(
          CodigoWeb.FORMATO_NAO_SUPORTADO, "Envie uma foto em JPEG, PNG ou WebP.");
    }
  }

  private static boolean comeca(byte[] bytes, int posicao, byte[] esperado) {
    return bytes.length >= posicao + esperado.length
        && Arrays.equals(bytes, posicao, posicao + esperado.length, esperado, 0, esperado.length);
  }

  /** Lê só o cabeçalho: uma bomba de descompressão é recusada antes de ser decodificada. */
  private void exigirDimensoesAceitas(Path entrada, long prazoFinal) {
    long largura = lado(entrada, "width", prazoFinal, true);
    long altura = lado(entrada, "height", prazoFinal, true);
    if (largura > LADO_MAXIMO || altura > LADO_MAXIMO || largura * altura > AREA_MAXIMA) {
      throw invalida();
    }
  }

  /**
   * Um lado pelo {@code vipsheader}. Na entrada do usuário, falha é imagem inválida; num arquivo
   * nosso, é falha do processamento.
   */
  private int lado(Path arquivo, String campo, long prazoFinal, boolean doUsuario) {
    Resultado resultado =
        executar(prazoFinal, config.vipsheader(), "-f", campo, arquivo.toString());
    try {
      if (resultado.codigo() == 0) {
        return Integer.parseInt(resultado.saida());
      }
    } catch (NumberFormatException e) {
      // cai na recusa abaixo
    }
    if (doUsuario) {
      throw invalida();
    }
    LOG.warn(
        "vipsheader falhou num arquivo gerado pelo processamento (código {})", resultado.codigo());
    throw indisponivel();
  }

  /** Passo sobre um arquivo que o próprio processamento gerou: falha não é culpa do usuário. */
  private void derivar(long prazoFinal, String... argumentos) {
    List<String> comando = new ArrayList<>();
    comando.add(config.vips());
    comando.addAll(List.of(argumentos));
    Resultado resultado = executar(prazoFinal, comando.toArray(String[]::new));
    if (resultado.codigo() != 0) {
      LOG.warn("vips {} falhou (código {})", argumentos[0], resultado.codigo());
      throw indisponivel();
    }
  }

  private static void exigirSucesso(Resultado resultado) {
    if (resultado.codigo() != 0) {
      throw invalida();
    }
  }

  private void exigirVersaoSuportada() {
    Resultado resultado;
    try {
      resultado =
          executar(System.nanoTime() + config.prazo().toNanos(), config.vips(), "--version");
    } catch (ErroDeNegocio e) {
      throw new IllegalStateException(VERSAO_EXIGIDA, e);
    }
    Matcher versao = VERSAO.matcher(resultado.saida());
    if (resultado.codigo() != 0 || !versao.find()) {
      throw new IllegalStateException(VERSAO_EXIGIDA);
    }
    int maior = Integer.parseInt(versao.group(1));
    int menor = Integer.parseInt(versao.group(2));
    if (maior < 8 || (maior == 8 && menor < 15)) {
      throw new IllegalStateException(VERSAO_EXIGIDA + " (encontrado " + versao.group() + ")");
    }
  }

  /**
   * Roda um comando sem shell. Prazo estourado mata o processo e os descendentes e vira 503; a
   * saída padrão é pequena (só o {@code vipsheader} e o {@code --version} escrevem) e é lida no
   * fim.
   */
  private Resultado executar(long prazoFinal, String... argumentos) {
    if (prazoFinal - System.nanoTime() <= 0) {
      throw indisponivel();
    }
    ProcessBuilder construtor =
        new ProcessBuilder(argumentos).redirectError(ProcessBuilder.Redirect.DISCARD);
    construtor.environment().put("VIPS_BLOCK_UNTRUSTED", "1");
    construtor.environment().put("VIPS_CONCURRENCY", "1");
    Process processo;
    try {
      processo = construtor.start();
    } catch (IOException e) {
      LOG.error("Não foi possível iniciar o {}", argumentos[0], e);
      throw indisponivel();
    }
    try {
      processo.getOutputStream().close();
      if (!processo.waitFor(prazoFinal - System.nanoTime(), TimeUnit.NANOSECONDS)) {
        LOG.warn("{} passou do prazo e foi encerrado", argumentos[0]);
        matar(processo);
        throw indisponivel();
      }
      String saida = new String(processo.getInputStream().readAllBytes(), US_ASCII).trim();
      return new Resultado(processo.exitValue(), saida);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      matar(processo);
      throw indisponivel();
    } catch (IOException e) {
      matar(processo);
      LOG.warn("Falha de E/S ao falar com o {}", argumentos[0], e);
      throw indisponivel();
    }
  }

  private static void matar(Process processo) {
    processo.descendants().forEach(ProcessHandle::destroyForcibly);
    processo.destroyForcibly();
    try {
      processo.waitFor(1, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private static void apagar(Path... arquivos) {
    for (Path arquivo : arquivos) {
      try {
        Files.deleteIfExists(arquivo);
      } catch (IOException e) {
        LOG.warn("Não foi possível apagar um temporário de imagem", e);
      }
    }
  }

  private static Timer timer(MeterRegistry metricas, String resultado) {
    return Timer.builder("duora.imagens.processamento")
        .description("Processamento de imagens enviadas, do pedido de vaga ao fim")
        .tag("resultado", resultado)
        .register(metricas);
  }

  private static ErroDeNegocio invalida() {
    return new ErroDeNegocio(
        CodigoWeb.IMAGEM_INVALIDA, "Não conseguimos abrir esta imagem. Envie outra foto.");
  }

  private static ErroDeNegocio indisponivel() {
    return new ErroDeNegocio(
        CodigoWeb.PROCESSAMENTO_INDISPONIVEL,
        "Não conseguimos processar a foto agora. Tente de novo em alguns minutos.");
  }

  private record Resultado(int codigo, String saida) {}
}
