package bipotheking.net.duora.perfil;

/** As idades que a pessoa quer conhecer, de 18 a 99, com a mínima até a máxima. */
public record FaixaEtaria(int minima, int maxima) {

  private static final int MENOR_IDADE = 18;
  private static final int MAIOR_IDADE = 99;

  public static final FaixaEtaria TODAS = new FaixaEtaria(MENOR_IDADE, MAIOR_IDADE);

  /**
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code FAIXA_ETARIA_INVALIDA} fora de 18 a
   *     99 ou com a mínima acima da máxima
   */
  public FaixaEtaria {
    if (minima < MENOR_IDADE || maxima > MAIOR_IDADE || minima > maxima) {
      throw CodigoPerfil.FAIXA_ETARIA_INVALIDA.erro();
    }
  }

  public boolean contem(int idade) {
    return idade >= minima && idade <= maxima;
  }
}
