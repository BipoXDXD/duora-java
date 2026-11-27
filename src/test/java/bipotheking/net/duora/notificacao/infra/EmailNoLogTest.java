package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class EmailNoLogTest {

  private static final String LINK = "https://meetduora.com/entrar/confirmar#t=abc";

  private final EmailNoLog email = new EmailNoLog();

  private static MensagemDeEmail para(String destinatario, String texto) {
    return new MensagemDeEmail(destinatario, "Seu link de entrada", texto, "<p>" + texto + "</p>");
  }

  @Test
  void logaDestinatarioMascaradoEOTexto(CapturedOutput saida) {
    email.enviar(para("ana@exemplo.com", "Entre pelo link: " + LINK), UUID.randomUUID());

    assertThat(saida.getOut())
        .contains("a***@exemplo.com")
        .contains(LINK)
        .doesNotContain("ana@exemplo.com");
  }

  @Test
  void guardaAs50Ultimas() {
    for (int i = 0; i <= 50; i++) {
      email.enviar(para("pessoa" + i + "@exemplo.com", "texto " + i), UUID.randomUUID());
    }

    assertThat(email.ultimaPara("pessoa0@exemplo.com")).isEmpty();
    assertThat(email.ultimaPara("pessoa1@exemplo.com")).isPresent();
    assertThat(email.ultimaPara("pessoa50@exemplo.com")).isPresent();
  }

  @Test
  void ultimaParaDevolveAMaisRecente() {
    email.enviar(para("ana@exemplo.com", "primeiro"), UUID.randomUUID());
    email.enviar(para("bia@exemplo.com", "outra pessoa"), UUID.randomUUID());
    email.enviar(para("ana@exemplo.com", "segundo"), UUID.randomUUID());

    assertThat(email.ultimaPara("ana@exemplo.com")).map(MensagemDeEmail::texto).hasValue("segundo");
  }
}
