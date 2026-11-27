package bipotheking.net.duora.notificacao;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MensagemDeEmailTest {

  @Test
  void toStringNaoExpoeDestinatarioNemConteudo() {
    MensagemDeEmail mensagem =
        new MensagemDeEmail(
            "ana@exemplo.com", "Seu link de entrada", "texto-CANARY", "<p>html-CANARY</p>");

    assertThat(mensagem.toString())
        .contains("a***@exemplo.com")
        .doesNotContain("ana@exemplo.com")
        .doesNotContain("CANARY");
  }
}
