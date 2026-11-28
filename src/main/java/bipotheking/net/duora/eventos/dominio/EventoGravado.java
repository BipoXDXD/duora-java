package bipotheking.net.duora.eventos.dominio;

/**
 * Um evento como saiu do banco, pronto para o stream.
 *
 * @param seq o {@code id:} do SSE, crescente por conta na ordem do commit
 * @param nome o {@code event:} do SSE
 * @param dadosJson o {@code data:} do SSE, já em JSON
 */
public record EventoGravado(long seq, String nome, String dadosJson) {}
