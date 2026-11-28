package bipotheking.net.duora.perfil;

/** O que a tela mostra no lugar da distância: cada faixa inclui o seu limite de cima. */
public enum FaixaDeDistancia {
  ATE_5_KM,
  ATE_20_KM,
  ATE_50_KM,
  MAIS_DE_50_KM;

  public static FaixaDeDistancia de(double km) {
    if (km <= 5) {
      return ATE_5_KM;
    }
    if (km <= 20) {
      return ATE_20_KM;
    }
    if (km <= 50) {
      return ATE_50_KM;
    }
    return MAIS_DE_50_KM;
  }
}
