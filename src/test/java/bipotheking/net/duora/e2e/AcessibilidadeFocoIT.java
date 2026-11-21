package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.assertions.PlaywrightAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.ReducedMotion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Gestão de foco e anúncios (WCAG 2.2 AA): o foco nunca cai no body quando o elemento focado some
 * numa troca de tela, o estado de "O que falta" chega ao leitor como texto, o painel é o conteúdo
 * principal e o {@code role=status} anuncia o que o parceiro faz, uma vez por mudança. O relógio do
 * servidor é o real com um adiantamento que o teste controla (para a presença expirar sem esperar
 * 15 s).
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AcessibilidadeFocoIT.RelogioConfig.class)
class AcessibilidadeFocoIT {

  private static final String ANA = "Ana";
  private static final String BRUNO = "Bruno";

  /**
   * Sonda na página: registra o texto acessível do {@code #aviso} (sem os nós aria-hidden) a cada
   * mutação que um leitor de tela anunciaria: texto inserido ou alterado fora de aria-hidden.
   */
  private static final String SONDA_ANUNCIOS =
      """
      (() => {
        const anuncios = [];
        window.__anuncios = anuncios;
        const oculto = (no) => {
          const el = no.nodeType === Node.ELEMENT_NODE ? no : no.parentElement;
          return Boolean(el?.closest('[aria-hidden="true"]'));
        };
        const textoAcessivel = (aviso) => {
          const copia = aviso.cloneNode(true);
          copia.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
          return copia.textContent.replace(/\\s+/g, " ").trim();
        };
        new MutationObserver((mudancas) => {
          for (const m of mudancas) {
            const alvo = m.target.nodeType === Node.ELEMENT_NODE ? m.target : m.target.parentElement;
            const aviso = alvo?.closest?.("#aviso");
            if (!aviso) continue;
            const anuncia = m.type === "characterData"
              ? !oculto(m.target)
              : [...m.addedNodes].some((n) => !oculto(n) && n.textContent.trim() !== "");
            if (anuncia) anuncios.push(textoAcessivel(aviso));
          }
        }).observe(document, { childList: true, subtree: true, characterData: true });
      })();
      """;

  private static Playwright playwright;
  private static Browser browser;

  @Value("${local.server.port}")
  private int porta;

  @Autowired private RelogioAdiantavel relogio;

  private final List<BrowserContext> contextos = new ArrayList<>();

  @TestConfiguration
  static class RelogioConfig {
    @Bean
    @Primary
    RelogioAdiantavel relogioAdiantavel() {
      return new RelogioAdiantavel();
    }
  }

  /** Relógio do sistema mais um adiantamento acumulado. */
  static final class RelogioAdiantavel extends Clock {
    private final AtomicReference<Duration> adiantamento = new AtomicReference<>(Duration.ZERO);

    void avancar(Duration duracao) {
      adiantamento.updateAndGet(atual -> atual.plus(duracao));
    }

    @Override
    public Instant instant() {
      return Instant.now().plus(adiantamento.get());
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  @BeforeAll
  static void abrirNavegador() {
    playwright = Playwright.create();
    browser = Navegador.abrir(playwright);
    PlaywrightAssertions.setDefaultAssertionTimeout(10_000);
  }

  @AfterAll
  static void fecharNavegador() {
    browser.close();
    playwright.close();
  }

  @AfterEach
  void fecharContextos() {
    contextos.forEach(BrowserContext::close);
    contextos.clear();
  }

  // ------------------------------------------------------------ foco nas trocas de tela

  @Test
  void criarTerracoLevaOFocoAoLinkDoConvite() {
    TerracoPagina ana = participante();

    ana.abrir(raiz()).criarTerraco(ANA);

    assertThat(ana.page().getByLabel("Link para a outra pessoa")).isFocused();
  }

  @Test
  void entrarNoTerracoLevaOFocoAPrimeiraPecaDoInventario() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    ana.abrir(raiz()).criarTerraco(ANA);

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(bruno.itemDoInventario(BANCO)).isFocused();
  }

  @Test
  void conviteQueViraMontagemLevaOFocoDeAnaAPrimeiraPeca() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    ana.abrir(raiz()).criarTerraco(ANA);
    ana.botao("Copiar convite").focus();

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(ana.itemDoInventario(TELESCOPIO)).isFocused();
  }

  @Test
  void conviteQueViraMontagemNaoTiraOFocoDeQuemEstaEmOutroControle() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    ana.abrir(raiz()).criarTerraco(ANA);
    ana.botao("Sair").focus();

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    assertThat(ana.botao("Sair")).isFocused();
  }

  @Test
  void parceiroQueEntraComAConfirmacaoAbertaNaoTiraOFocoEAtualizaOTexto() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    ana.abrir(raiz()).criarTerraco(ANA);
    ana.botaoSair().click();
    assertThat(ana.botaoContinuarNoTerraco()).isFocused();

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    assertThat(ana.confirmacaoDeSaida())
        .hasAccessibleName("Isso encerra o terraço para Bruno também.");
    assertThat(ana.botao("Encerrar para os dois")).isVisible();
    assertThat(ana.botaoContinuarNoTerraco()).isFocused();
  }

  @Test
  void revelacaoLevaOFocoAoTituloDoResultadoNosDois() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);
    comporTerraco(ana, bruno);

    ana.confirmar();
    bruno.botaoPronto().click();

    assertThat(bruno.tituloDoResultado()).isFocused();
    assertThat(ana.tituloDoResultado()).isFocused();
  }

  @Test
  void ajustarDeNovoLevaOFocoAPrimeiraPeca() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.tituloDoResultado()).isFocused();

    ana.botao("Ajustar de novo").click();

    assertThat(ana.itemDoInventario(TELESCOPIO)).isFocused();
  }

  // ------------------------------------------------------------ estrutura

  @Test
  void oQueFaltaDizProntoOuFaltaEmTextoParaOLeitor() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);
    Locator falta =
        ana.page()
            .getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("O que falta"))
            .getByRole(AriaRole.LIST);

    assertThat(falta)
        .matchesAriaSnapshot("- list:\n  - listitem: /^Telescópio no centro ?, falta$/");

    ana.colocar(TELESCOPIO, C1, ANA);

    assertThat(falta)
        .matchesAriaSnapshot("- list:\n  - listitem: /^Telescópio no centro ?, pronto$/");
  }

  @Test
  void painelDoTerracoEOConteudoPrincipal() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);

    Locator principal =
        ana.page()
            .getByRole(AriaRole.MAIN, new Page.GetByRoleOptions().setName("Painel do terraço"));

    assertThat(principal).hasCount(1);
    assertThat(
            principal.getByRole(
                AriaRole.REGION, new Locator.GetByRoleOptions().setName("Suas peças")))
        .isVisible();
  }

  // ------------------------------------------------------------ anúncios

  @Test
  void pecasDoParceiroSaoAnunciadasUmaVezCadaEAsPropriasNao() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);

    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.aviso()).hasText("Bruno colocou o banco: centro, à direita.");
    assertThat(bruno.aviso()).hasText("");

    bruno.selecionar(BANCO);
    bruno.lugarLivre(BANCO, C1).click();
    assertThat(ana.aviso()).hasText("Bruno moveu o banco: centro, à esquerda.");

    ana.colocar(TELESCOPIO, C2, ANA);
    assertThat(bruno.aviso()).hasText("Ana colocou o telescópio: centro, à direita.");

    bruno.colocar(LANTERNA, R1, BRUNO);
    assertThat(ana.aviso()).hasText("Bruno colocou a lanterna: fundo, à direita.");

    bruno.selecionar(LANTERNA);
    bruno.botao("Tirar do terraço").click();
    assertThat(ana.aviso()).hasText("Bruno tirou a lanterna do terraço.");

    // Duas consultas a mais (2 s cada) sem mudança: nada é reanunciado.
    ana.page().waitForTimeout(4_500);
    assertEquals(
        List.of(
            "Bruno entrou no terraço. Escolham as primeiras peças.",
            "Bruno colocou o banco: centro, à direita.",
            "Bruno moveu o banco: centro, à esquerda.",
            "Bruno colocou a lanterna: fundo, à direita.",
            "Bruno tirou a lanterna do terraço."),
        anuncios(ana));
  }

  @Test
  void parceiroSemSinalEDeVoltaSaoAnunciados() {
    TerracoPagina ana = participante();
    TerracoPagina bruno = participante();
    entrarEmPar(ana, bruno);
    BrowserContext contextoDeBruno = bruno.page().context();

    bruno.page().close();
    relogio.avancar(Duration.ofSeconds(16));
    assertThat(ana.aviso()).hasText("Bruno ficou sem sinal.");

    new TerracoPagina(contextoDeBruno.newPage()).abrir(raiz());
    assertThat(ana.aviso()).hasText("Bruno está de volta.");
  }

  @Test
  void quedaDeConexaoEAnunciadaUmaVezEAVoltaTambem() {
    TerracoPagina ana = participante();
    ana.abrir(raiz()).criarTerraco(ANA);
    ana.linkDoConvite();
    AtomicInteger falhas = new AtomicInteger(2);
    ana.page()
        .route(
            "**/api/sala",
            rota -> {
              if ("GET".equals(rota.request().method()) && falhas.getAndDecrement() > 0) {
                rota.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(500)
                        .setContentType("application/problem+json")
                        .setBody("{\"status\":500,\"codigo\":\"ERRO_INTERNO\"}"));
              } else {
                rota.resume();
              }
            });

    assertThat(ana.aviso()).hasText("Sem conexão com o servidor. Nova tentativa em 2 s.");
    assertThat(ana.aviso()).hasText("Sem conexão com o servidor. Nova tentativa em 4 s.");
    assertThat(ana.aviso()).hasText("Conexão de volta.");

    assertEquals(List.of("Sem conexão com o servidor.", "Conexão de volta."), anuncios(ana));
  }

  // ------------------------------------------------------------ apoio

  private TerracoPagina participante() {
    BrowserContext contexto =
        browser.newContext(
            new Browser.NewContextOptions()
                .setViewportSize(1440, 900)
                .setReducedMotion(ReducedMotion.REDUCE));
    contextos.add(contexto);
    contexto.addInitScript(SONDA_ANUNCIOS);
    return new TerracoPagina(contexto.newPage());
  }

  private String raiz() {
    return "http://127.0.0.1:" + porta + "/";
  }

  private void entrarEmPar(TerracoPagina ana, TerracoPagina bruno) {
    ana.abrir(raiz()).criarTerraco(ANA);
    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
  }

  private void comporTerraco(TerracoPagina ana, TerracoPagina bruno) {
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();
    bruno.colocar(LANTERNA, R1, BRUNO);
    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
  }

  @SuppressWarnings("unchecked")
  private static List<String> anuncios(TerracoPagina pagina) {
    return (List<String>) pagina.page().evaluate("() => window.__anuncios");
  }
}
