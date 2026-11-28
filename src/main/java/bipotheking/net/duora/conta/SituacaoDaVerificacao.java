package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import java.util.Objects;

/**
 * A verificação de idade da conta agora: o estado, o que ainda cabe nas últimas 24 h e quem atende
 * a captura.
 */
public record SituacaoDaVerificacao(
    EstadoDaVerificacao estado,
    int tentativasRestantesHoje,
    int enviosDeDocumentoRestantesHoje,
    ModoDeCaptura modo) {

  public SituacaoDaVerificacao {
    Objects.requireNonNull(estado, "estado");
    Objects.requireNonNull(modo, "modo");
  }
}
