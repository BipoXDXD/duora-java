package bipotheking.net.duora.conta;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.web.BancoDeTeste;
import bipotheking.net.duora.web.MesmaOrigem;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;

class SessaoApiTest extends BancoDeTeste {

  private static final String NOME_DO_COOKIE = "duora_sessao";
  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final Cookie COOKIE_INVALIDO = new Cookie(NOME_DO_COOKIE, "invalido");

  @Autowired private ContasDeTeste contas;

  @Test
  void ativaRecebeOsTresCampos() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    obterConta(cookieDe(conta))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .json(
                    "{\"estado\":\"ATIVA\",\"cadastroConcluido\":true,\"verificada\":false}",
                    JsonCompareMode.STRICT));
  }

  @Test
  void aguardandoRecebeCadastroConcluidoFalse() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO);

    obterConta(cookieDe(conta))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("AGUARDANDO_VERIFICACAO"))
        .andExpect(jsonPath("$.cadastroConcluido").value(false));
  }

  @Test
  void aguardandoNaRotaSoAtivaRecebe403CadastroIncompleto() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO);

    mvc.perform(get("/api/teste/so-ativa").cookie(cookieDe(conta)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CADASTRO_INCOMPLETO"))
        .andExpect(jsonPath("$.mensagem").value("Conclua o cadastro para continuar."));
  }

  @Test
  void pendenteRecebeCadastroConcluidoFalse() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.PENDENTE, null);

    obterConta(cookieDe(conta))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("PENDENTE"))
        .andExpect(jsonPath("$.cadastroConcluido").value(false));
  }

  @Test
  void suspensaRecebe403ContaIndisponivel() throws Exception {
    ContaDeTeste conta = contas.criar(EstadoConta.SUSPENSA, NASCIMENTO);

    obterConta(cookieDe(conta))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.codigo").value("CONTA_INDISPONIVEL"))
        .andExpect(jsonPath("$.mensagem").value("Esta conta não está disponível."));
  }

  @Test
  void contaSemSessaoResponde204() throws Exception {
    mvc.perform(get("/api/conta"))
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void exclusaoSemSessaoResponde401() throws Exception {
    mvc.perform(
            post("/api/conta/exclusao")
                .header(MesmaOrigem.CABECALHO, "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmo\":true}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
        .andExpect(jsonPath("$.mensagem").value("Entre para continuar."));
  }

  @Test
  void sairSemSessaoRecebe401SemSetCookie() throws Exception {
    mvc.perform(post("/api/conta/sair").header(MesmaOrigem.CABECALHO, "1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
        .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
  }

  @Test
  void cookieInvalidoEmGetContaRecebe204EApagaCookie() throws Exception {
    obterConta(COOKIE_INVALIDO)
        .andExpect(status().isNoContent())
        .andExpect(content().string(""))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(NOME_DO_COOKIE + "=;")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
  }

  @Test
  void cookieInvalidoEmRotaProtegidaRecebe401EApagaCookie() throws Exception {
    mvc.perform(get("/api/teste/so-ativa").cookie(COOKIE_INVALIDO))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(NOME_DO_COOKIE + "=;")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));
  }

  @Test
  void renovacaoReemiteCookie() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(Duration.ofHours(24).plusSeconds(1));

    obterConta(cookieDe(conta))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(conta.cookie() + ";")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=2592000")));
  }

  @Test
  void sairApagaCookieESessao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();

    mvc.perform(post("/api/conta/sair").header(MesmaOrigem.CABECALHO, "1").cookie(cookieDe(conta)))
        .andExpect(status().isNoContent())
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(NOME_DO_COOKIE + "=;")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

    obterConta(cookieDe(conta)).andExpect(status().isNoContent());
    mvc.perform(get("/api/teste/so-ativa").cookie(cookieDe(conta)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
  }

  @Test
  void cookieSeguroPorPadrao() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(Duration.ofHours(24).plusSeconds(1));

    obterConta(cookieDe(conta))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("; Secure")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("; HttpOnly")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("; SameSite=Lax")))
        .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("; Path=/;")));
  }

  private ResultActions obterConta(Cookie cookie) throws Exception {
    return mvc.perform(get("/api/conta").cookie(cookie));
  }

  /** O {@code ContaDeTeste} traz o cookie como {@code nome=valor}. */
  private static Cookie cookieDe(ContaDeTeste conta) {
    String[] partes = conta.cookie().split("=", 2);
    return new Cookie(partes[0], partes[1]);
  }
}
