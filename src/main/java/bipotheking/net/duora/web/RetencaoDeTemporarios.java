package bipotheking.net.duora.web;

import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Temporários do processador de imagens ({@code <dir>/tmp}) vivem uma hora pelo mtime: o normal é o
 * próprio processamento apagá-los, e esta política só varre o que um processo interrompido deixou.
 * Ao subir, a aplicação apaga só o que passou de {@link #IDADE_DE_ABANDONO}: no deploy sem parada,
 * o contêiner antigo ainda processa na mesma pasta, e esvaziá-la inteira quebraria esses envios.
 */
@Component
class RetencaoDeTemporarios implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("PT1H");

  /**
   * Bem acima do maior processamento possível (prazo de 15 s, espera de 5 s e moderação de 4 s): um
   * temporário mais velho que isso não tem mais dono.
   */
  static final Duration IDADE_DE_ABANDONO = Duration.ofMinutes(2);

  private final Path tmp;
  private final Clock relogio;

  RetencaoDeTemporarios(ConfigDeImagens config, Clock relogio) {
    this.tmp = config.dir().resolve("tmp");
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "imagens-temporarias";
  }

  @Override
  public Duration prazo() {
    return PRAZO;
  }

  @Override
  public int expirar(Instant limite) {
    int apagados = 0;
    for (Path arquivo : anterioresA(limite)) {
      if (apagar(arquivo)) {
        apagados++;
      }
    }
    return apagados;
  }

  @Override
  public long tamanho() {
    return anterioresA(relogio.instant().minus(PRAZO)).size();
  }

  @EventListener(ApplicationReadyEvent.class)
  void apagarAbandonadosNoBoot() {
    expirar(relogio.instant().minus(IDADE_DE_ABANDONO));
  }

  private List<Path> anterioresA(Instant limite) {
    return arquivos().stream().filter(arquivo -> modificadoAntesDe(arquivo, limite)).toList();
  }

  /** Os arquivos comuns de {@code tmp}; vazio se a pasta ainda não existe. */
  private List<Path> arquivos() {
    if (!Files.isDirectory(tmp)) {
      return List.of();
    }
    try (Stream<Path> conteudo = Files.list(tmp)) {
      return conteudo.filter(Files::isRegularFile).toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível listar os temporários de imagem", e);
    }
  }

  /** Um arquivo que some no meio da varredura (o processamento terminou) não venceu. */
  private static boolean modificadoAntesDe(Path arquivo, Instant limite) {
    try {
      return Files.getLastModifiedTime(arquivo).toInstant().isBefore(limite);
    } catch (NoSuchFileException e) {
      return false;
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível ler um temporário de imagem", e);
    }
  }

  private static boolean apagar(Path arquivo) {
    try {
      return Files.deleteIfExists(arquivo);
    } catch (IOException e) {
      throw new UncheckedIOException("Não foi possível apagar um temporário de imagem", e);
    }
  }
}
