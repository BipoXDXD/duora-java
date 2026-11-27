package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import org.junit.jupiter.api.Test;

class MensagensDeEntradaTest {

  private static final Email PARA = Email.de("ana@exemplo.com");
  private static final String LINK = "https://meetduora.com/entrar/confirmar#t=abc_-123";

  private final MensagensDeEntrada mensagens =
      new MensagensDeEntrada(new SitePublico("https://meetduora.com"));

  @Test
  void linkComOsTextosDoPlano() {
    MensagemDeEmail mensagem = mensagens.link(PARA, "abc_-123");

    assertThat(mensagem.para()).isEqualTo("ana@exemplo.com");
    assertThat(mensagem.assunto()).isEqualTo("Seu link para entrar no Duora");
    assertThat(mensagem.texto())
        .isEqualTo(
            "Olá! Use este link para entrar no Duora. Ele vale por 15 minutos e só funciona uma"
                + " vez:\n\n"
                + LINK
                + "\n\nSe você não pediu, ignore este e-mail.");
    assertThat(mensagem.html())
        .isEqualTo(
            "<p>Olá! Use este link para entrar no Duora. Ele vale por 15 minutos e só funciona"
                + " uma vez:</p>\n<p><a href=\""
                + LINK
                + "\">"
                + LINK
                + "</a></p>\n<p>Se você não pediu, ignore este e-mail.</p>");
  }

  @Test
  void avisoDeBloqueioSemLink() {
    MensagemDeEmail mensagem = mensagens.avisoDeBloqueio(PARA);

    assertThat(mensagem.para()).isEqualTo("ana@exemplo.com");
    assertThat(mensagem.assunto()).isEqualTo("Cadastro no Duora");
    assertThat(mensagem.texto())
        .isEqualTo("Não foi possível criar uma conta no Duora com este e-mail.");
    assertThat(mensagem.html())
        .isEqualTo("<p>Não foi possível criar uma conta no Duora com este e-mail.</p>");
  }
}
