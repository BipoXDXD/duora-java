package bipotheking.net.duora.conta.dominio;

import java.util.Objects;
import java.util.Optional;

/**
 * O que a prova de vida devolveu.
 *
 * @param confianca confiança da prova de vida, de 0 a 100
 * @param faixa idade estimada, presente só quando a prova achou exatamente um rosto
 * @param referencia imagem de referência do rosto, para comparar com o documento; quem decide pela
 *     selfie a descarta ao sair do método
 */
public record ResultadoDaProva(
    SituacaoDaProva situacao,
    double confianca,
    Optional<FaixaEstimada> faixa,
    Optional<Imagem> referencia) {

  public ResultadoDaProva {
    Objects.requireNonNull(situacao, "situacao");
    Objects.requireNonNull(faixa, "faixa");
    Objects.requireNonNull(referencia, "referencia");
  }
}
