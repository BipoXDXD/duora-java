package bipotheking.net.duora.notificacao;

import bipotheking.net.duora.nucleo.EmailMascarado;
import java.util.Objects;

/**
 * Um e-mail a enviar, com as versões em texto puro e em HTML. É a carga das mensagens {@link
 * Outbox#EMAIL}.
 *
 * @param para endereço do destinatário
 */
public record MensagemDeEmail(String para, String assunto, String texto, String html) {

  public MensagemDeEmail {
    Objects.requireNonNull(para, "para");
    Objects.requireNonNull(assunto, "assunto");
    Objects.requireNonNull(texto, "texto");
    Objects.requireNonNull(html, "html");
  }

  /** Destinatário mascarado e sem o conteúdo, que costuma levar um link de entrada. */
  @Override
  public String toString() {
    return "MensagemDeEmail[para=" + EmailMascarado.mascarar(para) + ", assunto=" + assunto + "]";
  }
}
