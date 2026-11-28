package bipotheking.net.duora.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Armazém no disco local: {@code <dir>/<categoria>/<2 últimos hex do id>/<id>-nitida.webp} e {@code
 * -borrada.webp}. Arquivos 0600 e pastas 0700: só o processo do Duora lê as fotos. Os temporários
 * do processador ficam em {@code <dir>/tmp}, no mesmo sistema de arquivos, e chegam aqui por {@code
 * rename} atômico: ninguém lê uma variante pela metade.
 */
@Component
class ArmazemEmDisco implements ArmazemDeImagens {

  private static final Pattern CATEGORIA = Pattern.compile("^[a-z]+$");
  private static final Pattern NOME =
      Pattern.compile(
          "^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-(?:"
              + Variante.NITIDA.sufixo()
              + "|"
              + Variante.BORRADA.sufixo()
              + ")\\.webp$");
  private static final Set<PosixFilePermission> SO_DONO_LE =
      PosixFilePermissions.fromString("rw-------");
  private static final FileAttribute<Set<PosixFilePermission>> PASTA_SO_DO_DONO =
      PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"));

  private final Path dir;

  ArmazemEmDisco(ConfigDeImagens config) {
    this.dir = config.dir();
  }

  @Override
  public void guardar(String categoria, UUID id, ImagemProcessada imagem) {
    Path pasta = pasta(categoria, id);
    Path nitida = arquivo(pasta, id, Variante.NITIDA);
    try {
      Files.createDirectories(pasta, PASTA_SO_DO_DONO);
      mover(imagem.nitida(), nitida);
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível guardar a imagem", e);
    }
    try {
      mover(imagem.borrada(), arquivo(pasta, id, Variante.BORRADA));
    } catch (IOException e) {
      apagarSemFalhar(nitida, e);
      throw new UncheckedIOException("Não foi possível guardar a imagem", e);
    }
  }

  private static void mover(Path origem, Path destino) throws IOException {
    Files.setPosixFilePermissions(origem, SO_DONO_LE);
    Files.move(origem, destino, StandardCopyOption.ATOMIC_MOVE);
  }

  /** Desfaz a nítida já movida: uma foto sem borrada não pode aparecer pela metade. */
  private static void apagarSemFalhar(Path arquivo, IOException causa) {
    try {
      Files.deleteIfExists(arquivo);
    } catch (IOException e) {
      causa.addSuppressed(e);
    }
  }

  @Override
  public Optional<Resource> ler(String categoria, UUID id, Variante variante) {
    Path arquivo = arquivo(pasta(categoria, id), id, variante);
    return Files.isRegularFile(arquivo)
        ? Optional.of(new FileSystemResource(arquivo))
        : Optional.empty();
  }

  @Override
  public void apagar(String categoria, UUID id) {
    Path pasta = pasta(categoria, id);
    try {
      for (Variante variante : Variante.values()) {
        Files.deleteIfExists(arquivo(pasta, id, variante));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível apagar a imagem", e);
    }
  }

  @Override
  public List<UUID> guardadasAntesDe(
      String categoria, Instant limite, @Nullable UUID depoisDe, int maximo) {
    if (maximo < 1) {
      throw new IllegalArgumentException("maximo precisa ser positivo: " + maximo);
    }
    Path raiz = raizDa(categoria);
    if (!Files.isDirectory(raiz)) {
      return List.of();
    }
    // Texto do UUID em minúsculas: a ordem lexicográfica é a dos bytes sem sinal, a mesma do
    // Postgres (UUID.compareTo compara com sinal e poria "f…" antes de "1…").
    TreeMap<String, Instant> maisRecentePorId = new TreeMap<>();
    try (Stream<Path> arquivos = Files.walk(raiz, 2)) {
      arquivos.forEach(arquivo -> registrar(arquivo, maisRecentePorId));
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível listar as imagens", e);
    }
    Map<String, Instant> depois =
        depoisDe == null ? maisRecentePorId : maisRecentePorId.tailMap(depoisDe.toString(), false);
    return depois.entrySet().stream()
        .filter(guardada -> guardada.getValue().isBefore(limite))
        .limit(maximo)
        .map(guardada -> UUID.fromString(guardada.getKey()))
        .toList();
  }

  /**
   * Anota o mtime da variante; o id fica com o mais recente das duas, para não varrer um par cuja
   * borrada acabou de chegar. Um arquivo que some no meio da listagem é ignorado.
   */
  private static void registrar(Path arquivo, Map<String, Instant> maisRecentePorId) {
    var nome = NOME.matcher(arquivo.getFileName().toString());
    if (!nome.matches() || !Files.isRegularFile(arquivo)) {
      return;
    }
    Instant mtime;
    try {
      mtime = Files.getLastModifiedTime(arquivo).toInstant();
    } catch (NoSuchFileException e) {
      return;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    maisRecentePorId.merge(nome.group(1), mtime, (a, b) -> a.isAfter(b) ? a : b);
  }

  private Path raizDa(String categoria) {
    if (!CATEGORIA.matcher(categoria).matches()) {
      throw new IllegalArgumentException("Categoria de imagem inválida: " + categoria);
    }
    return dir.resolve(categoria);
  }

  private Path pasta(String categoria, UUID id) {
    String texto = id.toString();
    return raizDa(categoria).resolve(texto.substring(texto.length() - 2));
  }

  private static Path arquivo(Path pasta, UUID id, Variante variante) {
    return pasta.resolve(id + "-" + variante.sufixo() + ".webp");
  }
}
