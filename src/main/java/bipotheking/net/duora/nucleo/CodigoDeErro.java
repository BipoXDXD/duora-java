package bipotheking.net.duora.nucleo;

/**
 * Código estável de uma recusa, lido pelo cliente no campo {@code codigo}. Cada módulo declara os
 * seus num enum que implementa esta interface.
 */
public interface CodigoDeErro {

  /** Nome do código no contrato, como {@code EMAIL_INVALIDO}. */
  String name();

  TipoDeErro tipo();

  /** Título curto do problema, em pt-BR. */
  String titulo();
}
