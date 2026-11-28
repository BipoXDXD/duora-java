package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.game.Terraco;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/encontros/{id}/assento}: a conta da sessão pega o cookie do assento dela. O
 * {@code instance} do problem+json ecoa o caminho pedido, então os corpos são comparados sem ele.
 */
class AssentoApiTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1995, 3, 1);

  @Autowired private ContasDeTeste contas;
  @Autowired private PonteDeEncontros ponte;

  @Test
  void assentoDevolveVisaoECookie() throws Exception {
    ContaDeTeste ana = contas.criar("Ana", NASCIMENTO);
    ContaDeTeste bruno = contas.criar("Bruno", NASCIMENTO);
    String id = abrir(ana, bruno);

    pedirAssento(id, ana)
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("duora_assento=")))
        .andExpect(jsonPath("$.encontroId").value(id))
        .andExpect(jsonPath("$.voce.nome").value("Ana"));
  }

  @Test
  void contaDeForaRecebe404SemDados() throws Exception {
    ContaDeTeste ana = contas.criar("Ana", NASCIMENTO);
    ContaDeTeste bruno = contas.criar("Bruno", NASCIMENTO);
    ContaDeTeste carla = contas.criar("Carla", NASCIMENTO);
    String id = abrir(ana, bruno);

    MockHttpServletResponse deFora = pedirAssento(id, carla).andReturn().getResponse();
    MockHttpServletResponse inexistente =
        pedirAssento("0123456789abcdef", carla).andReturn().getResponse();

    assertThat(deFora.getStatus()).isEqualTo(404);
    assertThat(deFora.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    assertThat(semInstance(deFora))
        .contains("ENCONTRO_INEXISTENTE")
        .isEqualTo(semInstance(inexistente));
    assertThat(inexistente.getStatus()).isEqualTo(404);
  }

  @Test
  void semSessao401() throws Exception {
    ContaDeTeste ana = contas.criar("Ana", NASCIMENTO);
    ContaDeTeste bruno = contas.criar("Bruno", NASCIMENTO);
    String id = abrir(ana, bruno);

    mvc.perform(post("/api/encontros/{id}/assento", id).header(MesmaOrigem.CABECALHO, "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  private String abrir(ContaDeTeste a, ContaDeTeste b) {
    return ponte
        .abrirPareado(new ParPareado(a.id(), b.id(), Set.of(Intencao.AMIZADE)), Terraco.ID)
        .encontroId();
  }

  private ResultActions pedirAssento(String id, ContaDeTeste conta) throws Exception {
    return mvc.perform(
        post("/api/encontros/{id}/assento", id)
            .header(MesmaOrigem.CABECALHO, "1")
            .cookie(cookieDe(conta)));
  }

  /** O corpo com o valor de {@code instance} esvaziado; o resto, byte a byte. */
  private static String semInstance(MockHttpServletResponse resposta) throws Exception {
    return resposta
        .getContentAsString(StandardCharsets.UTF_8)
        .replaceFirst("\"instance\":\"[^\"]*\"", "\"instance\":\"\"");
  }

  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
