package bipotheking.net.duora.conta;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.web.BancoDeTeste;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class LegalApiTest extends BancoDeTeste {

  private static final String DATA = "\\d{4}-\\d{2}-\\d{2}";

  @Test
  void versoesSemSessao() throws Exception {
    mvc.perform(get("/api/legal/versoes"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
        .andExpect(
            jsonPath("$.keys()")
                .value(containsInAnyOrder("termos", "privacidade", "contato", "encarregado")))
        .andExpect(jsonPath("$.termos.versao").value(matchesPattern(DATA)))
        .andExpect(jsonPath("$.privacidade.versao").value(matchesPattern(DATA)))
        .andExpect(jsonPath("$.contato").value("support@meetduora.com"))
        .andExpect(jsonPath("$.encarregado.nome").value("o responsável pelo Duora"))
        .andExpect(jsonPath("$.encarregado.email").value("encarregado@exemplo.invalid"));
  }

  @Test
  void documentoSemSessao() throws Exception {
    mvc.perform(get("/api/legal/termos"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.versao").value(matchesPattern(DATA)))
        .andExpect(jsonPath("$.html").value(startsWith("<h1>Termos de uso</h1>")));
  }

  @Test
  void documentoDesconhecido404() throws Exception {
    mvc.perform(get("/api/legal/cookies"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("DOCUMENTO_INEXISTENTE"));
  }
}
