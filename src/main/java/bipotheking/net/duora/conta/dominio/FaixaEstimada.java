package bipotheking.net.duora.conta.dominio;

/**
 * Faixa de idade, em anos, que a estimativa facial atribui a um rosto.
 *
 * @throws IllegalArgumentException se {@code baixa} for negativa ou maior que {@code alta}
 */
public record FaixaEstimada(int baixa, int alta) {

  public FaixaEstimada {
    if (baixa < 0 || baixa > alta) {
      throw new IllegalArgumentException("faixa estimada inválida: " + baixa + "–" + alta);
    }
  }
}
