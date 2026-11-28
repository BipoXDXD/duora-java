package bipotheking.net.duora.web;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.Resource;

/**
 * Onde as imagens processadas ficam guardadas, as duas variantes de cada uma sob o mesmo id. A
 * categoria separa os donos ({@code perfil}) e precisa casar com {@code ^[a-z]+$}; fora disso,
 * {@link IllegalArgumentException}. Falha de disco vira {@link java.io.UncheckedIOException}.
 */
public interface ArmazemDeImagens {

  /**
   * Move as duas variantes para o armazém, substituindo as que houver com o mesmo id. Os
   * temporários de {@code imagem} deixam de existir; fechá-la depois continua seguro.
   */
  void guardar(String categoria, UUID id, ImagemProcessada imagem);

  /** A variante guardada, ou vazio se não existe. */
  Optional<Resource> ler(String categoria, UUID id, Variante variante);

  /** Apaga as duas variantes. Idempotente: id ausente não é erro. */
  void apagar(String categoria, UUID id);

  /**
   * Uma página dos ids guardados antes de {@code limite} (mtime estritamente anterior), em ordem
   * crescente do id como texto (a mesma do {@code uuid} do Postgres), começando depois do cursor
   * {@code depoisDe} (nulo = do começo). Quem varre pede a próxima página com o último id recebido
   * até ela vir vazia.
   *
   * @param maximo tamanho da página, positivo
   */
  List<UUID> guardadasAntesDe(
      String categoria, Instant limite, @Nullable UUID depoisDe, int maximo);
}
