package bipotheking.net.duora.perfil;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * O que a própria conta vê das suas fotos: a aprovada (a que os outros veem), a pendente (só ela
 * vê) e quando a última foto foi recusada. Vira o {@code "foto"} do {@code GET /api/perfil}.
 */
public record FotoDoTitular(
    @Nullable UrlsDaFoto aprovada, @Nullable UrlsDaFoto pendente, @Nullable Instant recusadaEm) {}
