package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.nucleo.Correlacao;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

class IdDeCorrelacaoTest extends BancoDeTeste {

  private static final String ROTA = "/teste/log";
  private static final String RECEBIDO = "teste-1234-abcd";
  private static final String UUID_V7 =
      "^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$";

  @Test
  void devolveOValorRecebido() throws Exception {
    mvc.perform(get(ROTA).header(IdDeCorrelacao.CABECALHO, RECEBIDO))
        .andExpect(status().isOk())
        .andExpect(header().string(IdDeCorrelacao.CABECALHO, RECEBIDO));
  }

  @Test
  void semCabecalhoGeraUuidV7() throws Exception {
    String id = idDevolvido(mvc.perform(get(ROTA)).andExpect(status().isOk()));

    assertThat(id).matches(UUID_V7);
  }

  @Test
  void valorInvalidoETrocado() throws Exception {
    String id =
        idDevolvido(
            mvc.perform(get(ROTA).header(IdDeCorrelacao.CABECALHO, "<script>"))
                .andExpect(status().isOk()));

    assertThat(id).matches(UUID_V7);
  }

  @Test
  void presenteNaRecusaDeHost() throws Exception {
    mvc.perform(get(ROTA).header(HttpHeaders.HOST, "exemplo.com"))
        .andExpect(status().is(421))
        .andExpect(header().exists(IdDeCorrelacao.CABECALHO));
  }

  @Test
  void mdcLimpoDepois() throws Exception {
    mvc.perform(get(ROTA).header(IdDeCorrelacao.CABECALHO, RECEBIDO)).andExpect(status().isOk());

    assertThat(MDC.get(Correlacao.CHAVE_MDC)).isNull();
  }

  private static String idDevolvido(ResultActions resultado) {
    String id = resultado.andReturn().getResponse().getHeader(IdDeCorrelacao.CABECALHO);
    assertThat(id).isNotNull();
    return id;
  }
}
