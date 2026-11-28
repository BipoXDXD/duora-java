package bipotheking.net.duora.perfil;

/**
 * Resposta do envio: a foto entra pendente, e a URL é a da nítida. É também a resposta que a
 * idempotência guarda e devolve igual numa repetição.
 */
public record FotoEnviada(String estado, String url) {}
