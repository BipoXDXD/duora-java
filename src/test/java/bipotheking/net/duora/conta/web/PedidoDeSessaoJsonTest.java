package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * O corpo do pedido de sessão é estrito e a finalidade tem padrão. Roda com os falsos da V4, no
 * contexto comum, sem os dublês da verificação.
 */
class PedidoDeSessaoJsonTest extends BancoDeTeste {

  @Autowired private ContasDeTeste contas;

  @Test
  void campoExtraRecebe400() throws Exception {
    ContaDeTeste conta =
        contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10));

    pedir(conta, "{\"consentimentoBiometrico\":true,\"finalidade\":\"SELFIE\",\"extra\":1}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("CORPO_INVALIDO"));

    assertThat(tentativas()).isZero();
  }

  @Test
  void finalidadeAusenteViraSelfie() throws Exception {
    ContaDeTeste conta =
        contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10));

    pedir(conta, "{\"consentimentoBiometrico\":true}").andExpect(status().isCreated());

    assertThat(
            jdbc.sql("select finalidade from tentativa_verificacao where conta_id = :conta")
                .param("conta", conta.id().valor())
                .query(String.class)
                .list())
        .containsExactly("SELFIE");
  }

  private ResultActions pedir(ContaDeTeste conta, String corpo) throws Exception {
    String[] cookie = conta.cookie().split("=", 2);
    return mvc.perform(
        post("/api/conta/verificacao/sessao")
            .header(MesmaOrigem.CABECALHO, "1")
            .contentType(MediaType.APPLICATION_JSON)
            .content(corpo)
            .cookie(new Cookie(cookie[0], cookie[1])));
  }

  private int tentativas() {
    return jdbc.sql("select count(*) from tentativa_verificacao").query(Integer.class).single();
  }
}
