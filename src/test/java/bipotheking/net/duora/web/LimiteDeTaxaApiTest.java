package bipotheking.net.duora.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.RequestBuilder;

class LimiteDeTaxaApiTest extends BancoDeTeste {

  private static RequestBuilder pedidoDoIp(String ip) {
    return get("/teste/limite")
        .with(r -> {
          r.setRemoteAddr(ip);
          return r;
        });
  }

  @Test
  void quartaChamadaRecebe429ComRetryAfter() throws Exception {
    for (int i = 0; i < 3; i++) {
      mvc.perform(pedidoDoIp("10.0.0.7")).andExpect(status().isNoContent());
    }

    mvc.perform(pedidoDoIp("10.0.0.7"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
  }
}
