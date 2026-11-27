package bipotheking.net.duora.conta;

/**
 * Versão publicada de um documento legal: a data do front matter ({@code AAAA-MM-DD}) e o SHA-256
 * do texto servido, em hexadecimal minúsculo de 64 caracteres.
 */
public record VersaoDeDocumento(String versao, String resumoSha256) {}
