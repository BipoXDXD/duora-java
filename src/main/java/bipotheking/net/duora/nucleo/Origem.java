package bipotheking.net.duora.nucleo;

/** De onde veio uma requisição: IP e porta de origem, sem depender de servlet. */
public record Origem(String ip, int porta) {}
