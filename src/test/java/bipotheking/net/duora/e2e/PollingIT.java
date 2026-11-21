package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Clock;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Laço de polling de GET /api/sala com o relógio do Playwright pausado: o tempo só anda com {@code
 * runFor}, então cada espera é verificada na fronteira (ms − 1 não consulta; ms consulta), sem
 * sleep real. O servidor é o de verdade; {@code page.route} só injeta falhas ou segura uma resposta
 * quando o teste pede.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class PollingIT {

  private static final long INICIO = 1_800_000_000_000L;
  private static final int NORMAL = 2_000;
  private static final int UM_MINUTO = 60_000;

  /**
   * Sonda na página: registra cada GET /api/sala (hora falsa, If-None-Match, status, ETag), conta
   * consultas em voo e cada agendamento de setTimeout feito pelo laço de polling ({@code api.js},
   * identificado pela pilha; os meteoros também usam setTimeout e ficam de fora).
   */
  private static final String SONDA =
      """
      (() => {
        const r = { consultas: [], respostas: [], emVoo: 0, maxEmVoo: 0, agendas: [] };
        window.__sonda = r;
        const marcar = (nome, valor) => document.documentElement.setAttribute(nome, String(valor));
        const fetchOriginal = window.fetch.bind(window);
        window.fetch = (entrada, opcoes = {}) => {
          const metodo = (opcoes.method || "GET").toUpperCase();
          if (metodo !== "GET" || new URL(String(entrada), location.href).pathname !== "/api/sala") {
            return fetchOriginal(entrada, opcoes);
          }
          r.consultas.push({ t: Date.now(), inm: (opcoes.headers || {})["If-None-Match"] ?? null });
          r.emVoo += 1;
          r.maxEmVoo = Math.max(r.maxEmVoo, r.emVoo);
          marcar("data-em-voo", r.emVoo);
          return fetchOriginal(entrada, opcoes)
            .then(
              (resp) => { r.respostas.push({ status: resp.status, etag: resp.headers.get("ETag") }); return resp; },
              (erro) => { r.respostas.push({ status: 0, etag: null }); throw erro; })
            .finally(() => { r.emVoo -= 1; marcar("data-em-voo", r.emVoo); });
        };
        const agendarOriginal = window.setTimeout;
        window.setTimeout = function (fn, ms, ...args) {
          if (String(new Error().stack).includes("/js/api.js")) {
            r.agendas.push(ms ?? 0);
            marcar("data-agendas", r.agendas.length);
          }
          return agendarOriginal.call(window, fn, ms, ...args);
        };
      })();
      """;

  private enum Resposta {
    REAL,
    FALHA_500,
    FALHA_REDE,
    SEGURAR
  }

  @Value("${local.server.port}")
  private int porta;

  private final Deque<Resposta> roteiro = new ArrayDeque<>();
  private final List<Route> seguradas = new ArrayList<>();
  private Page page;
  private TerracoPagina ana;
  private int agendasVistas;

  @BeforeEach
  void anaCriaOTerracoComORelogioPausado(Participantes participantes) {
    ana = participantes.novo(this::prepararComRelogioPausado);
    page = ana.page();

    ana.abrir(raiz(porta));
    // Sem assento: a primeira consulta (agendada em 0 ms) leva à tela de entrada.
    assertEquals(0, proximoAgendamento());
    page.clock().runFor(0);
    ana.criarTerraco("Ana");
    ana.esperarLinkDoConvite();
    // A sessão nova reinicia o laço com uma consulta imediata, depois segue no ritmo normal.
    assertEquals(0, proximoAgendamento());
    page.clock().runFor(0);
    assertEquals(NORMAL, proximoAgendamento());
  }

  // Roda antes de a extensão fechar os contextos: nenhuma resposta fica pendurada.
  @AfterEach
  void soltarRespostasSeguradas() {
    seguradas.forEach(Route::abort);
  }

  @Test
  void consultaEmVooNuncaSeSobrepoeANovaConsulta() {
    roteiro.add(Resposta.SEGURAR);
    avancarAteConsultar(NORMAL);
    page.waitForCondition(() -> !seguradas.isEmpty());

    int consultasComUmaEmVoo = consultas();
    page.clock().runFor(UM_MINUTO);
    voltarParaAAba();
    page.clock().runFor(UM_MINUTO);
    assertEquals(consultasComUmaEmVoo, consultas(), "nova consulta com outra ainda em voo");

    seguradas.removeFirst().resume();
    // O pedido feito durante o voo vira uma consulta imediata, só depois da resposta.
    assertEquals(0, proximoAgendamento());
    avancarAteConsultar(0);
    assertEquals(NORMAL, proximoAgendamento());
    assertEquals(1, numero("window.__sonda.maxEmVoo"));
  }

  @Test
  void falhasTransitoriasRecuamAte15SegundosEVoltamA2DepoisDoSucesso() {
    roteiro.addAll(
        List.of(
            Resposta.FALHA_500,
            Resposta.FALHA_REDE,
            Resposta.FALHA_500,
            Resposta.FALHA_500,
            Resposta.FALHA_500));

    avancarAteConsultar(NORMAL);
    assertEquals(2_000, proximoAgendamento());
    assertThat(ana.aviso()).hasText("Sem conexão com o servidor. Nova tentativa em 2 s.");
    avancarAteConsultar(2_000);
    assertEquals(4_000, proximoAgendamento());
    assertThat(ana.aviso()).hasText("Sem conexão com o servidor. Nova tentativa em 4 s.");
    avancarAteConsultar(4_000);
    assertEquals(8_000, proximoAgendamento());
    avancarAteConsultar(8_000);
    assertEquals(15_000, proximoAgendamento());
    avancarAteConsultar(15_000);
    assertEquals(15_000, proximoAgendamento());

    avancarAteConsultar(15_000);
    assertEquals(NORMAL, proximoAgendamento());
    assertThat(ana.aviso()).hasText("Conexão de volta.");
    avancarAteConsultar(NORMAL);
    assertEquals(NORMAL, proximoAgendamento());
  }

  @Test
  void depoisDeSairNenhumaConsultaNovaEFeita() {
    roteiro.add(Resposta.SEGURAR);
    avancarAteConsultar(NORMAL);
    page.waitForCondition(() -> !seguradas.isEmpty());

    ana.sairDoTerraco();
    assertThat(ana.tituloEncerrado()).isVisible();
    // A consulta que estava em voo falha depois da saída: não pode reagendar nem avisar.
    seguradas.removeFirst().fulfill(new Route.FulfillOptions().setStatus(500));
    assertThat(page.locator("html")).hasAttribute("data-em-voo", "0");

    int consultasAoSair = consultas();
    voltarParaAAba();
    page.clock().runFor(5 * UM_MINUTO);

    assertEquals(consultasAoSair, consultas(), "consultou depois de sair");
    assertEquals(agendasVistas, numero("window.__sonda.agendas.length"), "reagendou após sair");
    assertThat(ana.tituloEncerrado()).isVisible();
    assertThat(ana.aviso()).hasText("");
  }

  @Test
  void respostaNaoModificadaNaoRedesenhaNemPerdeASelecao(Participantes participantes) {
    TerracoPagina bruno = participantes.novo();
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco("Bruno");
    assertThat(bruno.page().locator("#par")).containsText("Ana");
    avancarAteConsultar(NORMAL);
    assertEquals(NORMAL, proximoAgendamento());
    assertThat(page.locator("#par")).containsText("Bruno");
    ana.selecionar(TELESCOPIO);
    String etagDaVisao = texto("window.__sonda.respostas.at(-1).etag");
    page.evaluate(
        """
        () => {
          window.__mutacoes = 0;
          new MutationObserver((lista) => { window.__mutacoes += lista.length; })
            .observe(document.body, { subtree: true, childList: true, attributes: true,
                                      characterData: true });
        }
        """);

    for (int ciclo = 0; ciclo < 3; ciclo++) {
      avancarAteConsultar(NORMAL);
      assertEquals(NORMAL, proximoAgendamento());
      assertEquals(etagDaVisao, texto("window.__sonda.consultas.at(-1).inm"));
      assertEquals(304, numero("window.__sonda.respostas.at(-1).status"));
    }

    assertEquals(0, numero("window.__mutacoes"), "a página mudou com respostas 304");
    assertThat(ana.itemDoInventario(TELESCOPIO)).hasAttribute("aria-pressed", "true");
    assertThat(ana.lugarLivre(TELESCOPIO, C1)).isVisible();
  }

  // ------------------------------------------------------------ apoio

  private void prepararComRelogioPausado(Page pagina) {
    pagina.clock().install(new Clock.InstallOptions().setTime(INICIO));
    pagina.clock().pauseAt(INICIO + 1_000);
    pagina.addInitScript(SONDA);
    pagina.route("**/api/sala", this::responder);
  }

  private void responder(Route route) {
    if (!"GET".equals(route.request().method())) {
      route.resume();
      return;
    }
    Resposta resposta = roteiro.isEmpty() ? Resposta.REAL : roteiro.removeFirst();
    switch (resposta) {
      case REAL -> route.resume();
      case FALHA_500 ->
          route.fulfill(
              new Route.FulfillOptions()
                  .setStatus(500)
                  .setContentType("application/problem+json")
                  .setBody("{\"status\":500,\"codigo\":\"ERRO_INTERNO\"}"));
      case FALHA_REDE -> route.abort("failed");
      case SEGURAR -> seguradas.add(route);
    }
  }

  /** Verifica a fronteira da espera: em {@code ms − 1} nada sai; em {@code ms} sai uma consulta. */
  private void avancarAteConsultar(int ms) {
    int antes = consultas();
    if (ms > 0) {
      page.clock().runFor(ms - 1);
      assertEquals(antes, consultas(), "consultou antes de " + ms + " ms");
      page.clock().runFor(1);
    } else {
      page.clock().runFor(0);
    }
    assertEquals(antes + 1, consultas(), "não consultou depois de " + ms + " ms");
  }

  /** Espera a resposta ser tratada e devolve a espera escolhida para a próxima consulta. */
  private int proximoAgendamento() {
    agendasVistas += 1;
    assertThat(page.locator("html")).hasAttribute("data-agendas", String.valueOf(agendasVistas));
    return numero("window.__sonda.agendas.at(-1)");
  }

  private void voltarParaAAba() {
    page.evaluate("() => document.dispatchEvent(new Event('visibilitychange'))");
  }

  private int consultas() {
    return numero("window.__sonda.consultas.length");
  }

  private int numero(String expressao) {
    return ((Number) page.evaluate("() => " + expressao)).intValue();
  }

  private String texto(String expressao) {
    return (String) page.evaluate("() => " + expressao);
  }
}
