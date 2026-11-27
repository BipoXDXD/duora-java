package bipotheking.net.duora.conta.dominio;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Regra da verificação de idade, com limiares fixos em código: aprova pela selfie com faixa a
 * partir de {@value #IDADE_DE_APROVACAO} anos, recusa com faixa toda abaixo de 18 e exige confiança
 * mínima de {@value #CONFIANCA_MINIMA}. Confiança inválida ({@code NaN}) nunca passa do mínimo.
 */
public final class DecisaoDeIdade {

  public static final int IDADE_DE_APROVACAO = 21;
  public static final double CONFIANCA_MINIMA = 90.0;

  /** Mesma maioridade de {@link Idade#maiorDeIdade}: a faixa toda abaixo dela recusa. */
  private static final int MAIORIDADE = 18;

  private DecisaoDeIdade() {}

  /**
   * Decide pela estimativa facial: prova não concluída, com confiança abaixo do mínimo ou sem faixa
   * → {@code REPETIR}; faixa toda abaixo de 18 → {@code RECUSADA}; faixa toda a partir de {@value
   * #IDADE_DE_APROVACAO} → {@code APROVADA}; o resto → {@code DUVIDA}.
   */
  public static ResultadoDaVerificacao pelaSelfie(ResultadoDaProva prova) {
    Optional<FaixaEstimada> faixa = prova.faixa();
    if (!concluidaComConfianca(prova) || faixa.isEmpty()) {
      return ResultadoDaVerificacao.REPETIR;
    }
    if (faixa.get().alta() < MAIORIDADE) {
      return ResultadoDaVerificacao.RECUSADA;
    }
    if (faixa.get().baixa() >= IDADE_DE_APROVACAO) {
      return ResultadoDaVerificacao.APROVADA;
    }
    return ResultadoDaVerificacao.DUVIDA;
  }

  /**
   * A prova pode acompanhar o documento: concluída, com confiança mínima, faixa e imagem de
   * referência.
   */
  public static boolean provaServeParaDocumento(ResultadoDaProva prova) {
    return concluidaComConfianca(prova)
        && prova.faixa().isPresent()
        && prova.referencia().isPresent();
  }

  /**
   * Decide pelo documento, nesta ordem: não aceito ou sem data → {@code REPETIR}; rosto não confere
   * → {@code DUVIDA}; data lida com menos de 18 anos em {@code hoje} → {@code RECUSADA}; data lida
   * diferente da declarada → {@code DUVIDA}; senão {@code APROVADA}.
   */
  public static ResultadoDaVerificacao peloDocumento(
      LocalDate declarada, ResultadoDoDocumento documento, LocalDate hoje) {
    Optional<LocalDate> lida = documento.dataDeNascimento();
    if (!documento.documentoAceito() || lida.isEmpty()) {
      return ResultadoDaVerificacao.REPETIR;
    }
    if (!documento.rostoConfere()) {
      return ResultadoDaVerificacao.DUVIDA;
    }
    if (!Idade.maiorDeIdade(lida.get(), hoje)) {
      return ResultadoDaVerificacao.RECUSADA;
    }
    if (!lida.get().equals(declarada)) {
      return ResultadoDaVerificacao.DUVIDA;
    }
    return ResultadoDaVerificacao.APROVADA;
  }

  /** {@code >=} deixa {@code NaN} de fora, ao contrário de negar um {@code <}. */
  private static boolean concluidaComConfianca(ResultadoDaProva prova) {
    return prova.situacao() == SituacaoDaProva.CONCLUIDA && prova.confianca() >= CONFIANCA_MINIMA;
  }
}
