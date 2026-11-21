package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.ANA;
import static bipotheking.net.duora.e2e.Cenarios.BRUNO;
import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.C2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.L2;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R1;
import static bipotheking.net.duora.e2e.TerracoPagina.Lugar.R2;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.BANCO;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.LANTERNA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.MANTA;
import static bipotheking.net.duora.e2e.TerracoPagina.Peca.TELESCOPIO;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * Jornadas críticas em navegador real: cada participante tem o próprio BrowserContext (cookies
 * separados), como duas pessoas em navegadores diferentes.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class JornadaDoisNavegadoresIT {

  @Value("${local.server.port}")
  private int porta;

  private TerracoPagina ana;
  private TerracoPagina bruno;

  @BeforeEach
  void abrirParticipantes(Participantes participantes) {
    ana = participantes.novo();
    bruno = participantes.novo();
  }

  @Test
  void parCompletaAComposicaoEOsDoisVeemAMesmaNoiteRevelada() {
    ana.abrir(raiz(porta));
    ana.capturar("entrada");
    ana.criarTerraco(ANA);
    String convite = ana.linkDoConvite();
    ana.capturar("convite-aguardando");

    bruno.abrir(convite).entrarNoTerraco(BRUNO);

    ana.selecionar(TELESCOPIO);
    assertThat(ana.lugarLivre(TELESCOPIO, C1)).isVisible();
    ana.capturar("colocacao-lugares");
    ana.lugarLivre(TELESCOPIO, C1).click();
    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();

    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    ana.colocar(MANTA, L2, ANA);
    assertThat(bruno.pecaNoTerraco(MANTA, ANA, L2)).isVisible();
    bruno.colocar(LANTERNA, R1, BRUNO);

    assertThat(ana.pecaNoTerraco(LANTERNA, BRUNO, R1)).isVisible();
    ana.confirmar();
    ana.capturar("espera-confirmacao");
    bruno.botaoPronto().click();

    for (TerracoPagina participante : List.of(ana, bruno)) {
      assertThat(participante.app()).hasAttribute("data-momento", "noite");
      assertThat(participante.tituloDoResultado()).hasText("Uma noite de manta e lanterna");
    }
    ana.capturar("final-revelado");
  }

  @Test
  void mudancaDoParceiroDepoisDaConfirmacaoDesfazAConfirmacao() {
    montarComposicaoCompleta();
    ana.confirmar();

    bruno.selecionar(LANTERNA);
    bruno.lugarLivre(LANTERNA, R2).click();
    assertThat(bruno.pecaNoTerraco(LANTERNA, BRUNO, R2)).isVisible();

    assertThat(ana.botaoPronto()).isVisible();
    assertThat(ana.botaoVoltarAAjustar()).hasCount(0);
    assertThat(ana.app()).hasAttribute("data-momento", "crepusculo");
  }

  @Test
  void lugarOcupadoPeloParceiroMostraAvisoENaoDeslocaNada() {
    entrarEmPar();
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();

    bruno.selecionar(BANCO);
    // O lugar ocupado fica com aria-disabled; um clique de mouse ainda chega até ele.
    bruno.lugarOcupado(C1, ANA).click(new Locator.ClickOptions().setForce(true));

    assertThat(bruno.aviso()).hasText("Este lugar está ocupado por Ana. Escolha outro.");
    bruno.capturar("erro-lugar-ocupado");
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    assertThat(bruno.pecaDeNoTerraco(BANCO, BRUNO)).hasCount(0);
    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    assertThat(ana.pecaDeNoTerraco(BANCO, BRUNO)).hasCount(0);
  }

  @Test
  void recarregarAPaginaRecuperaAMesmaSalaEComposicao() {
    entrarEmPar();
    ana.colocar(TELESCOPIO, C1, ANA);
    assertThat(bruno.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    bruno.colocar(BANCO, C2, BRUNO);
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();

    ana.page().reload();

    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    assertThat(ana.pecaNoTerraco(BANCO, BRUNO, C2)).isVisible();
    assertThat(ana.page().locator("#par")).containsText(BRUNO);
    assertThat(ana.itemDoInventario(MANTA)).isVisible();
  }

  @Test
  void quandoUmSaiOOutroVeOTerracoEncerrado() {
    entrarEmPar();

    ana.sairDoTerraco();

    assertThat(ana.tituloEncerrado()).isVisible();
    assertThat(bruno.tituloEncerrado()).isVisible();
    assertThat(bruno.page().locator("#encerrado-texto"))
        .hasText("Ana saiu e o terraço foi encerrado.");
  }

  @Test
  void sairPedeConfirmacaoAntesDeEncerrar() {
    entrarEmPar();
    AtomicInteger saidas = contarSaidas(ana);

    ana.botaoSair().click();

    assertThat(ana.confirmacaoDeSaida()).isVisible();
    assertThat(ana.confirmacaoDeSaida())
        .hasAccessibleName("Isso encerra o terraço para Bruno também.");
    assertThat(ana.botao("Encerrar para os dois")).isVisible();
    assertThat(ana.botaoContinuarNoTerraco()).isFocused();
    assertThat(ana.botaoSair()).hasAttribute("aria-expanded", "true");
    assertThat(ana.botaoSair()).hasAttribute("aria-controls", "confirmar-saida");
    // Uma consulta completa de Bruno depois do clique: a sala continua ativa para ele.
    bruno.page().waitForResponse("**/api/sala", () -> {});
    assertThat(bruno.itemDoInventario(BANCO)).isVisible();
    assertThat(bruno.tituloEncerrado()).hasCount(0);
    assertEquals(0, saidas.get(), "o clique em Sair não pode encerrar sozinho");
  }

  @Test
  void continuarNoTerracoFechaAConfirmacao() {
    entrarEmPar();
    ana.botaoSair().click();

    ana.botaoContinuarNoTerraco().click();

    assertThat(ana.confirmacaoDeSaida()).hasCount(0);
    assertThat(ana.botaoSair()).isFocused();
    assertThat(ana.botaoSair()).hasAttribute("aria-expanded", "false");
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
  }

  @Test
  void escCancelaAConfirmacao() {
    entrarEmPar();
    ana.botaoSair().click();
    assertThat(ana.botaoContinuarNoTerraco()).isFocused();

    ana.page().keyboard().press("Escape");

    assertThat(ana.confirmacaoDeSaida()).hasCount(0);
    assertThat(ana.botaoSair()).isFocused();
    assertThat(ana.botaoSair()).hasAttribute("aria-expanded", "false");
  }

  @Test
  void confirmarEncerraParaOsDois() {
    entrarEmPar();
    AtomicInteger saidas = contarSaidas(ana);
    ana.botaoSair().click();

    ana.botao("Encerrar para os dois").dblclick();

    assertThat(ana.tituloEncerrado()).isFocused();
    assertThat(bruno.tituloEncerrado()).isVisible();
    assertEquals(1, saidas.get(), "o duplo clique deveria enviar um único POST de saída");
  }

  @Test
  void parceiroSaiDuranteAConfirmacao() {
    entrarEmPar();
    ana.botaoSair().click();
    assertThat(ana.confirmacaoDeSaida()).isVisible();

    bruno.sairDoTerraco();

    assertThat(ana.tituloEncerrado()).isVisible();
    assertThat(ana.page().locator("#confirmar-saida")).isHidden();
    assertThat(ana.page().locator("#encerrado-texto"))
        .hasText("Bruno saiu e o terraço foi encerrado.");
  }

  @Test
  void semParceiroAConfirmacaoEncerraSoOTerraco() {
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    ana.esperarLinkDoConvite();

    ana.botaoSair().click();

    assertThat(ana.confirmacaoDeSaida()).hasAccessibleName("Isso encerra o terraço.");
    assertThat(ana.botao("Encerrar para os dois")).hasCount(0);
    ana.botao("Encerrar o terraço").click();
    assertThat(ana.tituloEncerrado()).isFocused();
  }

  @Test
  void participanteColocaUmaPecaSoComTecladoComFocoVisivel() {
    entrarEmPar();
    var teclado = ana.page().keyboard();
    Locator telescopio = ana.itemDoInventario(TELESCOPIO);
    Locator primeiroLugar = ana.lugarLivre(TELESCOPIO, C1);

    ana.tabularAte(telescopio);
    assertFocoVisivel(telescopio);

    teclado.press("Enter");
    assertThat(primeiroLugar).isFocused();
    assertFocoVisivel(primeiroLugar);

    teclado.press("Escape");
    assertThat(primeiroLugar).hasCount(0);
    assertThat(telescopio).isFocused();
    assertThat(telescopio).hasAttribute("aria-pressed", "false");

    teclado.press("Enter");
    assertThat(primeiroLugar).isFocused();
    teclado.press("Enter");

    assertThat(ana.pecaNoTerraco(TELESCOPIO, ANA, C1)).isVisible();
    assertThat(telescopio).isFocused();
    assertFocoVisivel(telescopio);
  }

  @Test
  void terceiraPessoaComOMesmoConviteNaoEntra(Participantes participantes) {
    String convite = entrarEmPar();
    TerracoPagina terceira = participantes.novo();

    terceira.abrir(convite).entrarNoTerraco("Caio");

    // Convite de uso único: depois que Bruno entrou, o servidor responde CONVITE_INVALIDO.
    assertThat(terceira.aviso())
        .hasText("Este convite não vale mais. Peça um link novo ou crie seu próprio terraço.");
    assertThat(terceira.botao("Criar terraço")).isVisible();
    assertThat(terceira.page().locator("#montagem")).isHidden();
  }

  // ------------------------------------------------------------ apoio

  /** Ana cria, Bruno entra pelo convite; devolve o link usado. */
  private String entrarEmPar() {
    ana.abrir(raiz(porta)).criarTerraco(ANA);
    String convite = ana.linkDoConvite();
    bruno.abrir(convite).entrarNoTerraco(BRUNO);
    assertThat(ana.page().locator("#par")).containsText(BRUNO);
    assertThat(ana.itemDoInventario(TELESCOPIO)).isVisible();
    return convite;
  }

  private void montarComposicaoCompleta() {
    entrarEmPar();
    comporTerraco(ana, bruno);
  }

  /** Conta os POST /api/sala/sair que a página envia daqui em diante. */
  private static AtomicInteger contarSaidas(TerracoPagina participante) {
    AtomicInteger saidas = new AtomicInteger();
    participante
        .page()
        .onRequest(pedido -> {
          if ("POST".equals(pedido.method()) && pedido.url().endsWith("/api/sala/sair")) {
            saidas.incrementAndGet();
          }
        });
    return saidas;
  }

  private static void assertFocoVisivel(Locator alvo) {
    Object visivel =
        alvo.evaluate(
            """
            el => el.matches(':focus-visible') && [getComputedStyle(el),
                getComputedStyle(el, '::before')].some(s => s.boxShadow !== 'none'
                || (s.outlineStyle !== 'none' && s.outlineWidth !== '0px'))
            """);
    assertEquals(Boolean.TRUE, visivel, "o alvo focado deveria ter indicador de foco visível");
  }
}
