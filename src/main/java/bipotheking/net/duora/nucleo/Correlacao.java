package bipotheking.net.duora.nucleo;

/**
 * Chave do correlation ID no MDC: o filtro web a preenche por requisição e o despachante da outbox
 * por despacho, para todo log da mesma unidade de trabalho sair com o mesmo id.
 */
public final class Correlacao {

  public static final String CHAVE_MDC = "correlationId";

  private Correlacao() {}
}
