package bipotheking.net.duora.conta.dominio;

import org.jspecify.annotations.Nullable;

/**
 * Porta da verificação humana: confirma que o token do widget veio de uma pessoa. Falha fechada: na
 * dúvida, inclusive com o serviço fora do ar, devolve {@code false}.
 */
public interface VerificacaoHumana {

  /**
   * @param token o que o widget entregou; nulo, em branco ou longo demais é recusado sem rede
   * @param ip IP de origem do pedido, quando conhecido
   */
  boolean verificar(@Nullable String token, @Nullable String ip);
}
