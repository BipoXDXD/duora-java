package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@ExtendWith(NavegadorExtension.class)
class AcessibilidadeFocoIT {

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

  private static final Consumer<Page> COM_SONDA = page -> page.addInitScript(SONDA_ANUNCIOS);

  @Value("${local.server.port}")
  private int porta;

  @Autowired private RelogioAdiantavel relogio;

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

  // ------------------------------------------------------------ foco nas trocas de tela

  @Test
  void criarTerracoLevaOFocoAoLinkDoConvite(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);

    ana.abrir(raiz(porta)).criarTerraco(ANA);

    assertThat(ana.page().getByLabel("Link para a outra pessoa")).isFocused();
  }

  @Test
  void entrarNoTerracoLevaOFocoAPrimeiraPecaDoInventario(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    ana.abrir(raiz(porta)).criarTerraco(ANA);

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(bruno.itemDoInventario(BANCO)).isFocused();
  }

  @Test
  void conviteQueViraMontagemLevaOFocoDeAnaAPrimeiraPeca(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    ana.botao("Copiar convite").focus();

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(ana.itemDoInventario(TELESCOPIO)).isFocused();
  }

  @Test
  void conviteQueViraMontagemNaoTiraOFocoDeQuemEstaEmOutroControle(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    ana.botaoSair().focus();

    bruno.abrir(ana.linkDoConvite()).entrarNoTerraco(BRUNO);

    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    assertThat(ana.botaoSair()).isFocused();
  }

  @Test
  void parceiroQueEntraComAConfirmacaoAbertaNaoTiraOFocoEAtualizaOTexto(
      Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    ana.abrir(raiz(porta)).criarTerraco(ANA);
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
  void revelacaoLevaOFocoAoTituloDoResultadoNosDois(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);

    ana.confirmar();
    bruno.botaoPronto().click();

    assertThat(bruno.tituloDoResultado()).isFocused();
    assertThat(ana.tituloDoResultado()).isFocused();
  }

  @Test
  void ajustarDeNovoLevaOFocoAPrimeiraPeca(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.tituloDoResultado()).isFocused();

    ana.botao("Ajustar de novo").click();

    assertThat(ana.itemDoInventario(TELESCOPIO)).isFocused();
  }

  // ------------------------------------------------------------ foco em recusa e saída

  @Test
  void lugarRecusadoPeloServidorLevaOFocoAoPrimeiroLugarLivre(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
    // Bruno para de consultar a sala: continua vendo C1 livre depois que Ana o ocupa.
    List<Route> consultasRetidas = new CopyOnWriteArrayList<>();
    bruno
        .page()
        .route(
            "**/api/sala",
            rota -> {
              if ("GET".equals(rota.request().method())) {
                consultasRetidas.add(rota);
              } else {
                rota.resume();
              }
            });
    bruno.page().waitForCondition(() -> !consultasRetidas.isEmpty());
    ana.colocar(TELESCOPIO, C1, ANA);
    Locator c1 = bruno.lugarLivre(BANCO, C1);
    bruno.tabularAte(bruno.itemDoInventario(BANCO));
    bruno.page().keyboard().press("Enter");
    assertThat(c1).isFocused();

    bruno.page().keyboard().press("Enter");

    assertThat(bruno.aviso()).hasText("Ana acabou de usar este lugar. Escolha outro.");
    assertNotNull(lugarFocado(bruno), "o foco saiu dos lugares");
    assertNotEquals("C1", lugarFocado(bruno));
    assertNotEquals("true", desabilitadoNoFoco(bruno));
  }

  @Test
  void continuarDuranteOEnvioDaSaidaNaoFechaAConfirmacao(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
    // A saída fica pendente: o servidor nunca recebe o POST.
    ana.page().route("**/api/sala/sair", rota -> {});
    ana.botaoSair().click();
    ana.botao("Encerrar para os dois").click();
    assertThat(ana.botao("Enviando…")).isVisible();

    ana.botaoContinuarNoTerraco().click();

    assertThat(ana.page().locator("#confirmar-saida")).isVisible();
  }

  // ------------------------------------------------------------ estrutura

  @Test
  void oQueFaltaDizProntoOuFaltaEmTextoParaOLeitor(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
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
  void painelDoTerracoEOConteudoPrincipal(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));

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
  void pecasDoParceiroSaoAnunciadasUmaVezCadaEAsPropriasNao(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));

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
  void parceiroSemSinalEDeVoltaSaoAnunciados(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    TerracoPagina bruno = participantes.novo(COM_SONDA);
    entrarEmPar(ana, bruno, raiz(porta));
    BrowserContext contextoDeBruno = bruno.page().context();

    bruno.page().close();
    relogio.avancar(Duration.ofSeconds(16));
    assertThat(ana.aviso()).hasText("Bruno ficou sem sinal.");

    new TerracoPagina(contextoDeBruno.newPage()).abrir(raiz(porta));
    assertThat(ana.aviso()).hasText("Bruno está de volta.");
  }

  @Test
  void quedaDeConexaoEAnunciadaUmaVezEAVoltaTambem(Participantes participantes) {
    TerracoPagina ana = participantes.novo(COM_SONDA);
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    ana.esperarLinkDoConvite();
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

  @SuppressWarnings("unchecked")
  private static List<String> anuncios(TerracoPagina pagina) {
    return (List<String>) pagina.page().evaluate("() => window.__anuncios");
  }

  /** O {@code data-lugar} do elemento focado, ou nulo quando o foco não está num lugar. */
  private static String lugarFocado(TerracoPagina pagina) {
    return (String) pagina.page().evaluate("() => document.activeElement?.dataset?.lugar ?? null");
  }

  /** O {@code aria-disabled} do elemento focado, ou nulo quando ele não tem o atributo. */
  private static String desabilitadoNoFoco(TerracoPagina pagina) {
    return (String)
        pagina.page().evaluate("() => document.activeElement?.getAttribute('aria-disabled')");
  }
}
