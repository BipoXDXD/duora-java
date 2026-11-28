package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import java.util.Objects;

/**
 * O que uma tentativa decidiu, com o que ainda cabe nas últimas 24 h. É também a resposta guardada
 * pela idempotência: a repetição devolve o mesmo desfecho.
 *
 * @param pedeDocumento a selfie ficou em dúvida e ainda cabe um envio de documento hoje
 */
public record DesfechoDaVerificacao(
    ResultadoDaVerificacao resultado,
    boolean pedeDocumento,
    int tentativasRestantesHoje,
    int enviosDeDocumentoRestantesHoje) {

  public DesfechoDaVerificacao {
    Objects.requireNonNull(resultado, "resultado");
  }
}
