package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.nucleo.TipoDeErro;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TratamentoDeErrosTest {

  private static final String PROBLEMA = "application/problem+json";

  @Autowired private MockMvc mvc;

  @Test
  void erroDeNegocioViraProblemaComCodigoEMensagem() throws Exception {
    mvc.perform(get("/teste/erros/negocio"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
        .andExpect(jsonPath("$.mensagem").value("Entre para continuar."))
        .andExpect(jsonPath("$.detail").value("Entre para continuar."));
  }

  @Test
  void extrasViramCampos() throws Exception {
    mvc.perform(get("/teste/erros/extras"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
        .andExpect(jsonPath("$.visao.fase").value("MONTANDO"));
  }

  @Test
  void tentarDeNovoEmViraRetryAfter() throws Exception {
    mvc.perform(get("/teste/erros/limite"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().string(HttpHeaders.RETRY_AFTER, "900"));
  }

  @Test
  void falhaInesperadaNaoVaza() throws Exception {
    String canario = "CANARY-" + UUID.randomUUID();
    String corpo =
        mvc.perform(get("/teste/erros/inesperado").param("canario", canario))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
            .andExpect(jsonPath("$.codigo").value("ERRO_INTERNO"))
            .andExpect(jsonPath("$.mensagem").value(not(emptyString())))
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(corpo).doesNotContain(canario, "Exception", "at ", "bipotheking");
  }

  @Test
  void formatoNaoSuportadoTemCodigo() throws Exception {
    mvc.perform(post("/teste/erros/json").contentType(MediaType.TEXT_PLAIN).content("{}"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"))
        .andExpect(jsonPath("$.mensagem").value(not(emptyString())));
  }

  /** A mensagem de foto é do processador de imagem; o 415 do Spring segue genérico. */
  @Test
  void formatoNaoSuportadoDoSpringMantemMensagemGenerica() throws Exception {
    mvc.perform(post("/teste/erros/json").contentType(MediaType.TEXT_PLAIN).content("{}"))
        .andExpect(jsonPath("$.mensagem").value("Envie o conteúdo num formato aceito."));
  }

  @Test
  void uploadAcimaDoLimiteViraImagemGrandeDemais() throws Exception {
    mvc.perform(post("/teste/erros/upload-grande"))
        .andExpect(status().isContentTooLarge())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("IMAGEM_GRANDE_DEMAIS"))
        .andExpect(jsonPath("$.mensagem").value("A imagem pode ter até 5 MB."));
  }

  @Test
  void metodoNaoPermitidoTemCodigo() throws Exception {
    mvc.perform(post("/teste/erros/so-get"))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(header().exists(HttpHeaders.ALLOW))
        .andExpect(jsonPath("$.codigo").value("METODO_NAO_PERMITIDO"))
        .andExpect(jsonPath("$.mensagem").value(not(emptyString())));
  }

  @Test
  void cabecalhoAusenteTemCodigo() throws Exception {
    mvc.perform(get("/teste/erros/cabecalho"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"))
        .andExpect(jsonPath("$.mensagem").value(not(emptyString())));
  }

  @Test
  void filtroDeOrigemEscreveMensagem() throws Exception {
    mvc.perform(post("/api/salas").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isForbidden())
        .andExpect(content().contentTypeCompatibleWith(PROBLEMA))
        .andExpect(jsonPath("$.codigo").value("ORIGEM_RECUSADA"))
        .andExpect(jsonPath("$.mensagem").value(not(emptyString())));
  }

  @ParameterizedTest
  @CsvSource({
    "ENTRADA_INVALIDA, 400",
    "NAO_AUTENTICADO, 401",
    "PROIBIDO, 403",
    "NAO_ENCONTRADO, 404",
    "METODO_NAO_PERMITIDO, 405",
    "CONFLITO, 409",
    "ENCERRADO, 410",
    "GRANDE_DEMAIS, 413",
    "FORMATO_NAO_SUPORTADO, 415",
    "ENDERECO_RECUSADO, 421",
    "REGRA_DE_NEGOCIO, 422",
    "LIMITE, 429",
    "INTERNO, 500",
    "INDISPONIVEL, 503"
  })
  void cadaTipoTemSeuStatus(TipoDeErro tipo, int esperado) {
    assertThat(TratamentoDeErros.status(tipo).value()).isEqualTo(esperado);
  }
}
