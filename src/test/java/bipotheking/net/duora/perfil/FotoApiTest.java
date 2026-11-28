package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import bipotheking.net.duora.web.Variante;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/**
 * {@code POST /api/perfil/foto}: multipart com a parte {@code arquivo}, resposta 202 pendente.
 * {@code DELETE}: apaga aprovada, pendente e recusa, 204 mesmo sem foto.
 */
class FotoApiTest extends BancoDeTeste {

  private static final String CHAVE = "foto-teste-0001";
  private static final String URL_DA_NITIDA = "^/api/fotos/[0-9a-f-]{36}/nitida$";

  @Autowired private ContasDeTeste contas;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private FotosDoPerfil fotos;

  @Test
  void uploadResponde202Pendente() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    enviar(conta, retrato(), null)
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.estado").value("PENDENTE"))
        .andExpect(jsonPath("$.url").value(matchesPattern(URL_DA_NITIDA)));
  }

  @Test
  void mesmaChaveNaoCriaSegundaPendente() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    String primeira = corpo(enviar(conta, retrato(), CHAVE).andExpect(status().isAccepted()));

    String segunda = corpo(enviar(conta, retrato(), CHAVE).andExpect(status().isAccepted()));

    assertThat(segunda).isEqualTo(primeira);
    assertThat(fotosDa(conta)).isEqualTo(1);
  }

  @Test
  void mesmaChaveOutroArquivoRecusada() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    enviar(conta, retrato(), CHAVE).andExpect(status().isAccepted());

    enviar(conta, arquivo("paisagem.png", "image/png"), CHAVE)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.codigo").value("CHAVE_DE_IDEMPOTENCIA_REUSADA"));
  }

  @Test
  void rajadaDeUploadsBarrada() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    for (int i = 0; i < 10; i++) {
      enviar(conta, retrato(), null).andExpect(status().isAccepted());
    }

    enviar(conta, retrato(), null)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.codigo").value("LIMITE_DE_TAXA"))
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
  }

  @Test
  void semSessao401() throws Exception {
    mvc.perform(multipart("/api/perfil/foto").file(retrato()).header(MesmaOrigem.CABECALHO, "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void contaAguardandoVerificacao403() throws Exception {
    ContaDeTeste conta = contas.criarAguardando("foto", LocalDate.of(1990, 5, 10));

    enviar(conta, retrato(), null)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CADASTRO_INCOMPLETO"));
    assertThat(fotosDa(conta)).isZero();
  }

  @Test
  void textoRecebe415() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    enviar(conta, arquivo("texto.txt", "image/jpeg"), null)
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.codigo").value("FORMATO_NAO_SUPORTADO"));
    assertThat(fotosDa(conta)).isZero();
  }

  @Test
  void semParteArquivo400() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    MockMultipartFile outraParte =
        new MockMultipartFile(
            "foto", "retrato.jpg", "image/jpeg", FotosDeTeste.fixture(FotosDeTeste.RETRATO));

    enviar(conta, outraParte, null)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.codigo").value("REQUISICAO_INVALIDA"));
  }

  @Test
  void deleteRemoveTudo() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    FotoId aprovada = fotosDeTeste.enviarAprovada(conta.id());
    fotosDeTeste.enviar(conta.id());

    mvc.perform(
            delete("/api/perfil/foto").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());

    mvc.perform(get(UrlDaFoto.de(aprovada, Variante.NITIDA)).cookie(cookieDe(conta)))
        .andExpect(status().isNotFound());
    assertThat(fotos.doTitular(conta.id())).isEqualTo(new FotoDoTitular(null, null, null));
  }

  @Test
  void deleteSemFoto204() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    mvc.perform(
            delete("/api/perfil/foto").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)))
        .andExpect(status().isNoContent());
  }

  private ResultActions enviar(
      ContaDeTeste conta, MockMultipartFile arquivo, @Nullable String chave) throws Exception {
    MockMultipartHttpServletRequestBuilder pedido = multipart("/api/perfil/foto").file(arquivo);
    pedido.header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta));
    if (chave != null) {
      pedido.header("Idempotency-Key", chave);
    }
    return mvc.perform(pedido);
  }

  private static MockMultipartFile retrato() {
    return arquivo(FotosDeTeste.RETRATO, "image/jpeg");
  }

  private static MockMultipartFile arquivo(String fixture, String tipo) {
    return new MockMultipartFile("arquivo", fixture, tipo, FotosDeTeste.fixture(fixture));
  }

  private static String corpo(ResultActions resposta) throws Exception {
    return resposta.andReturn().getResponse().getContentAsString();
  }

  private long fotosDa(ContaDeTeste conta) {
    return jdbc.sql("select count(*) from foto where conta_id = :conta")
        .param("conta", conta.id().valor())
        .query(Long.class)
        .single();
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
