package bipotheking.net.duora.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MesmaOrigemTest {

  @Autowired private MockMvc mvc;

  /**
   * O handler mapping decodifica o caminho e ignora parâmetros de segmento: {@code /%61pi/salas} e
   * {@code /api;x/salas} chegam ao {@code POST /api/salas}. A checagem de origem precisa ver o
   * mesmo caminho, ou a mutação passa sem {@code X-Duora}.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/%61pi/salas", "/api;x/salas"})
  void prefixoDisfarcadoNaoEscapaDaChecagemDeOrigem(String caminho) throws Exception {
    mvc.perform(
            post("/")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apelido\":\"Ana\"}")
                .with(requisicao -> {
                  requisicao.setRequestURI(caminho);
                  return requisicao;
                }))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"));
  }
}
