package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Cada rota da casca de conta, aberta direto no navegador (link de e-mail, recarga), recebe o
 * index.html sem cache e com a CSP do app; só a /verificacao recebe a da prova de vida. O resto
 * continua 404.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RotasDoSpaTest {

  private static final Path CAMINHOS_DO_FRONT =
      Path.of("frontend", "src", "rotas", "caminhosDaCasca.ts");
  private static final Pattern LITERAL_DE_CAMINHO = Pattern.compile("\"(/[^\"]*)\"");
  private static final Pattern LISTA_DE_PREFIXOS =
      Pattern.compile("PREFIXOS_DA_CASCA\\s*=\\s*\\[([^\\]]*)\\]");
  private static final String CONTA_QUALQUER = "3f2a9c1e-0000-4000-8000-000000000001";

  @Autowired private MockMvc mockMvc;

  @ParameterizedTest
  @FieldSource("bipotheking.net.duora.web.RotasDoSpa#CAMINHOS")
  void cadaRotaDoSpaEncaminhaParaOIndex(String caminho) throws Exception {
    String csp =
        caminho.equals("/verificacao")
            ? CabecalhosDeSeguranca.CSP_VERIFICACAO
            : CabecalhosDeSeguranca.CSP;
    mockMvc
        .perform(get(caminho))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"))
        .andExpect(header().string("Cache-Control", "no-cache"))
        .andExpect(header().string("Content-Security-Policy", csp));
  }

  @Test
  void confirmarComTokenNaQueryNaoTemEfeito() throws Exception {
    mockMvc
        .perform(get("/entrar/confirmar").queryParam("t", "abc"))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"));
  }

  @Test
  void rotaDesconhecidaContinua404() throws Exception {
    mockMvc.perform(get("/nao-existe")).andExpect(status().isNotFound());
  }

  @Test
  void nenhumaRotaComecaPorApp() {
    assertThat(RotasDoSpa.CAMINHOS).noneMatch(caminho -> caminho.startsWith("/app/"));
  }

  @Test
  void prefixoDePerfisEncaminhaParaOIndex() throws Exception {
    mockMvc
        .perform(get("/perfis/" + CONTA_QUALQUER))
        .andExpect(status().isOk())
        .andExpect(forwardedUrl("/index.html"))
        .andExpect(header().string("Content-Security-Policy", CabecalhosDeSeguranca.CSP));
  }

  @ParameterizedTest
  @ValueSource(strings = {"/perfis", "/perfis/", "/perfis/a/b"})
  void perfisSemIdContinua404(String caminho) throws Exception {
    mockMvc.perform(get(caminho)).andExpect(status().isNotFound());
  }

  @Test
  void rotasIguaisAsDoFront() throws IOException {
    String fonte = Files.readString(CAMINHOS_DO_FRONT);
    Set<String> doFront = literais(fonte);
    doFront.removeAll(literais(listaDePrefixos(fonte)));

    assertThat(doFront).as("literais de %s", CAMINHOS_DO_FRONT).isNotEmpty();
    assertThat(Set.copyOf(RotasDoSpa.CAMINHOS)).isEqualTo(doFront);
  }

  @Test
  void prefixosIguaisAosDoFront() throws IOException {
    Set<String> doFront = literais(listaDePrefixos(Files.readString(CAMINHOS_DO_FRONT)));

    assertThat(doFront).as("PREFIXOS_DA_CASCA de %s", CAMINHOS_DO_FRONT).isNotEmpty();
    assertThat(Set.copyOf(RotasDoSpa.PREFIXOS)).isEqualTo(doFront);
  }

  private static Set<String> literais(String trecho) {
    return LITERAL_DE_CAMINHO
        .matcher(trecho)
        .results()
        .map(literal -> literal.group(1))
        .collect(Collectors.toCollection(HashSet::new));
  }

  private static String listaDePrefixos(String fonte) {
    Matcher lista = LISTA_DE_PREFIXOS.matcher(fonte);
    assertThat(lista.find()).as("PREFIXOS_DA_CASCA em %s", CAMINHOS_DO_FRONT).isTrue();
    return lista.group(1);
  }
}
