package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.Variante;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code GET /api/fotos/{id}/{nitida|borrada}}: sem permissão responde o mesmo 404 da foto que não
 * existe. O {@code instance} do problem+json ecoa o caminho pedido, então os corpos são comparados
 * sem ele.
 */
class AcessoAFotoApiTest extends BancoDeTeste {

  private static final String WEBP = "image/webp";

  @Autowired private ContasDeTeste contas;
  @Autowired private FotosDeTeste fotosDeTeste;
  @Autowired private FotosDoPerfil fotos;

  @Test
  void titularVeAPendenteNitida() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviar(titular.id());

    byte[] corpo =
        pedir(titular, UrlDaFoto.de(foto, Variante.NITIDA))
            .andExpect(status().isOk())
            .andExpect(content().contentType(WEBP))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    assertThat(new String(Arrays.copyOf(corpo, 4), StandardCharsets.US_ASCII)).isEqualTo("RIFF");
  }

  @Test
  void terceiroNaoVePendente() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    ContaDeTeste terceiro = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviar(titular.id());

    pedir(terceiro, UrlDaFoto.de(foto, Variante.BORRADA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("FOTO_INEXISTENTE"));
  }

  @Test
  void terceiroVeSoABorradaDaAprovada() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    ContaDeTeste terceiro = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviarAprovada(titular.id());
    pedir(terceiro, UrlDaFoto.de(foto, Variante.BORRADA))
        .andExpect(status().isOk())
        .andExpect(content().contentType(WEBP));

    pedir(terceiro, UrlDaFoto.de(foto, Variante.NITIDA))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.codigo").value("FOTO_INEXISTENTE"))
        .andExpect(jsonPath("$.mensagem").value(semOId(foto)))
        .andExpect(jsonPath("$.detail").value(semOId(foto)))
        .andExpect(jsonPath("$.title").value(semOId(foto)));
  }

  @Test
  void idInexistenteEProibidoRespondemIgual() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    ContaDeTeste terceiro = contas.criarAtiva();
    FotoId proibida = fotosDeTeste.enviar(titular.id());
    FotoId inexistente = new FotoId(UUID.randomUUID());

    MockHttpServletResponse deProibida =
        resposta(pedir(terceiro, UrlDaFoto.de(proibida, Variante.NITIDA)));
    MockHttpServletResponse deInexistente =
        resposta(pedir(terceiro, UrlDaFoto.de(inexistente, Variante.NITIDA)));

    assertThat(deProibida.getStatus()).isEqualTo(404);
    assertThat(deProibida.getStatus()).isEqualTo(deInexistente.getStatus());
    assertThat(deProibida.getContentType()).isEqualTo(deInexistente.getContentType());
    assertThat(semInstance(deProibida)).isEqualTo(semInstance(deInexistente));
  }

  @Test
  void fotoNovaNaoDerrubaAAprovada() {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId aprovada = fotosDeTeste.enviarAprovada(titular.id());
    fotosDeTeste.enviar(titular.id());

    assertThat(fotos.urlDaAprovada(titular.id(), Variante.BORRADA))
        .isEqualTo(Optional.of(UrlDaFoto.de(aprovada, Variante.BORRADA)));
  }

  @Test
  void nitidaSemCache() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviarAprovada(titular.id());

    MockHttpServletResponse resposta =
        resposta(pedir(titular, UrlDaFoto.de(foto, Variante.NITIDA)).andExpect(status().isOk()));

    assertThat(resposta.getHeaders(HttpHeaders.CACHE_CONTROL)).containsExactly("no-store");
  }

  @Test
  void borradaComCachePrivado() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    ContaDeTeste terceiro = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviarAprovada(titular.id());

    MockHttpServletResponse resposta =
        resposta(pedir(terceiro, UrlDaFoto.de(foto, Variante.BORRADA)).andExpect(status().isOk()));

    assertThat(resposta.getHeaders(HttpHeaders.CACHE_CONTROL))
        .containsExactly("private, max-age=300");
    assertThat(resposta.getHeaders(HttpHeaders.VARY))
        .anySatisfy(vary -> assertThat(vary).contains("Cookie"));
  }

  @Test
  void varianteDesconhecida404() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviarAprovada(titular.id());
    FotoId inexistente = new FotoId(UUID.randomUUID());

    MockHttpServletResponse original =
        resposta(pedir(titular, "/api/fotos/" + foto.valor() + "/original"));
    MockHttpServletResponse deInexistente =
        resposta(pedir(titular, UrlDaFoto.de(inexistente, Variante.NITIDA)));

    assertThat(original.getStatus()).isEqualTo(404);
    assertThat(semInstance(original)).isEqualTo(semInstance(deInexistente));
  }

  @Test
  void idMalformado404() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId inexistente = new FotoId(UUID.randomUUID());

    MockHttpServletResponse malformado = resposta(pedir(titular, "/api/fotos/nao-e-uuid/borrada"));
    MockHttpServletResponse deInexistente =
        resposta(pedir(titular, UrlDaFoto.de(inexistente, Variante.BORRADA)));

    assertThat(malformado.getStatus()).isEqualTo(404);
    assertThat(semInstance(malformado)).isEqualTo(semInstance(deInexistente));
  }

  @Test
  void semSessao401() throws Exception {
    ContaDeTeste titular = contas.criarAtiva();
    FotoId foto = fotosDeTeste.enviarAprovada(titular.id());

    mvc.perform(get(UrlDaFoto.de(foto, Variante.BORRADA)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  private ResultActions pedir(ContaDeTeste conta, String url) throws Exception {
    return mvc.perform(get(url).cookie(cookieDe(conta)));
  }

  private static MockHttpServletResponse resposta(ResultActions acoes) {
    return acoes.andReturn().getResponse();
  }

  /** O corpo com o valor de {@code instance} esvaziado; o resto, byte a byte. */
  private static String semInstance(MockHttpServletResponse resposta) throws Exception {
    return resposta
        .getContentAsString(StandardCharsets.UTF_8)
        .replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
  }

  private static Matcher<String> semOId(FotoId foto) {
    return not(containsString(foto.valor().toString()));
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
