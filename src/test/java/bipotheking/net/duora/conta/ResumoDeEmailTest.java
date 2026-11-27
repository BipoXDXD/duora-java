package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.nucleo.Resumos;
import org.junit.jupiter.api.Test;

class ResumoDeEmailTest {

  private static final String CHAVE = "chave-de-teste-com-pelo-menos-32-caracteres";

  private final ResumoDeEmail resumo = new ResumoDeEmail(CHAVE);

  @Test
  void variantesDoMesmoGmailTemOMesmoResumo() {
    assertThat(resumo.de(Email.de("Nome+x@gmail.com")))
        .isEqualTo(resumo.de(Email.de("n.ome@gmail.com")));
  }

  @Test
  void chavesDiferentesDaoResumosDiferentes() {
    Email email = Email.de("ana@exemplo.com");
    ResumoDeEmail outro = new ResumoDeEmail("outra-chave-de-teste-com-mais-de-32-caracteres");

    assertThat(resumo.de(email)).isNotEqualTo(outro.de(email));
  }

  @Test
  void resumoNaoEhSha256Puro() {
    Email email = Email.de("ana@exemplo.com");

    assertThat(resumo.de(email)).isNotEqualTo(Resumos.sha256(email.chaveDeBloqueio()));
  }

  @Test
  void hexTem64CaracteresHexadecimaisMinusculos() {
    assertThat(resumo.hex(Email.de("ana@exemplo.com"))).matches("[0-9a-f]{64}");
  }

  @Test
  void chaveCurtaEhRecusada() {
    assertThatThrownBy(() -> new ResumoDeEmail("x".repeat(31)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
