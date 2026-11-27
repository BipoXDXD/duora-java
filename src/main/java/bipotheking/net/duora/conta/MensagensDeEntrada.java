package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import org.springframework.stereotype.Component;

/**
 * Os e-mails do pedido de entrada: o link mágico e o aviso para e-mail bloqueado. O token vai no
 * fragmento ({@code #t=}), que o navegador não manda ao servidor nem a quem pré-carrega o link.
 */
@Component
class MensagensDeEntrada {

  private static final String CAMINHO_DO_LINK = "/entrar/confirmar#t=";
  private static final String ASSUNTO_DO_LINK = "Seu link para entrar no Duora";
  private static final String ABERTURA_DO_LINK =
      "Olá! Use este link para entrar no Duora. Ele vale por 15 minutos e só funciona uma vez:";
  private static final String FECHO_DO_LINK = "Se você não pediu, ignore este e-mail.";
  private static final String ASSUNTO_DO_AVISO = "Cadastro no Duora";
  private static final String TEXTO_DO_AVISO =
      "Não foi possível criar uma conta no Duora com este e-mail.";

  private final SitePublico site;

  MensagensDeEntrada(SitePublico site) {
    this.site = site;
  }

  /** O token é base64url e o endereço do site não tem consulta: o link dispensa escape no HTML. */
  MensagemDeEmail link(Email para, String token) {
    String link = site.endereco(CAMINHO_DO_LINK + token);
    String texto = ABERTURA_DO_LINK + "\n\n" + link + "\n\n" + FECHO_DO_LINK;
    String html =
        "<p>"
            + ABERTURA_DO_LINK
            + "</p>\n<p><a href=\""
            + link
            + "\">"
            + link
            + "</a></p>\n<p>"
            + FECHO_DO_LINK
            + "</p>";
    return new MensagemDeEmail(para.valor(), ASSUNTO_DO_LINK, texto, html);
  }

  MensagemDeEmail avisoDeBloqueio(Email para) {
    return new MensagemDeEmail(
        para.valor(), ASSUNTO_DO_AVISO, TEXTO_DO_AVISO, "<p>" + TEXTO_DO_AVISO + "</p>");
  }
}
