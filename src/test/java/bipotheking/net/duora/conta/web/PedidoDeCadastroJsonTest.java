package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.web.CadastroController.PedidoDeCadastroJson;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** O corpo do cadastro vai para o log do Spring MVC em DEBUG: a data de nascimento não aparece. */
class PedidoDeCadastroJsonTest {

  @Test
  void toStringOmiteDataDeNascimento() {
    PedidoDeCadastroJson corpo =
        new PedidoDeCadastroJson(LocalDate.of(1990, 5, 10), "2026-11-01", "2026-11-02", true);

    assertThat(corpo.toString())
        .isEqualTo(
            "PedidoDeCadastroJson[dataDeNascimento=***, versaoTermos=2026-11-01,"
                + " versaoPrivacidade=2026-11-02, aceito=true]")
        .doesNotContain("1990");
  }
}
