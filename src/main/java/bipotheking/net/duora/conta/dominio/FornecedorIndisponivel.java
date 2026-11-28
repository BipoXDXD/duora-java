package bipotheking.net.duora.conta.dominio;

import java.io.Serial;
import org.jspecify.annotations.Nullable;

/**
 * O fornecedor não respondeu como devia: 5xx, timeout, falha de rede, 401, 403 ou 429. É a única
 * exceção que os adapters lançam; o caso de uso a traduz em 503 com o código da porta. Qualquer
 * outro tipo de exceção vindo de um adapter é bug (500).
 */
public class FornecedorIndisponivel extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Cria a falha do fornecedor.
   *
   * @param motivo descrição curta da falha, sem corpo de resposta, chave, ARN nem imagem
   * @param causa exceção original, se houver
   */
  public FornecedorIndisponivel(String motivo, @Nullable Throwable causa) {
    super(motivo, causa);
  }
}
