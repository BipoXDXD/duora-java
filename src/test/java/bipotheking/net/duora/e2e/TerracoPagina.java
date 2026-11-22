package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.regex.Pattern;

/** Page Object do terraço: fala com a página só por papéis e rótulos acessíveis. */
final class TerracoPagina {

  enum Peca {
    TELESCOPIO("Telescópio", "o telescópio"),
    MANTA("Manta", "a manta"),
    RADIO("Rádio", "o rádio"),
    BANCO("Banco", "o banco"),
    LANTERNA("Lanterna", "a lanterna"),
    GARRAFA("Garrafa térmica", "a garrafa térmica");

    final String nome;
    final String artigo;

    Peca(String nome, String artigo) {
      this.nome = nome;
      this.artigo = artigo;
    }
  }

  enum Lugar {
    C1("centro, à esquerda"),
    C2("centro, à direita"),
    L1("fundo, à esquerda"),
    L2("frente, à esquerda"),
    R1("fundo, à direita"),
    R2("frente, à direita");

    final String nome;

    Lugar(String nome) {
      this.nome = nome;
    }
  }

  private static final Path CAPTURAS = Path.of("target", "e2e-capturas");
  private static final int[][] TAMANHOS = {{1440, 900}, {390, 844}};

  private final Page page;

  TerracoPagina(Page page) {
    this.page = page;
  }

  Page page() {
    return page;
  }

  TerracoPagina abrir(String url) {
    page.navigate(url);
    return this;
  }

  void criarTerraco(String apelido) {
    enviarApelido(apelido, "Criar terraço");
  }

  void entrarNoTerraco(String apelido) {
    enviarApelido(apelido, "Entrar no terraço");
  }

  private void enviarApelido(String apelido, String acao) {
    page.getByLabel("Seu apelido").fill(apelido);
    botao(acao).click();
  }

  /** Espera o servidor preencher o link do convite: só então a tela do convite está completa. */
  void esperarLinkDoConvite() {
    assertThat(campoDoConvite()).hasValue(Pattern.compile("/#convite=.+"));
  }

  /** Lê o link do campo somente leitura, esperando o servidor preenchê-lo. */
  String linkDoConvite() {
    esperarLinkDoConvite();
    return campoDoConvite().inputValue();
  }

  private Locator campoDoConvite() {
    return page.getByLabel("Link para a outra pessoa");
  }

  Locator itemDoInventario(Peca peca) {
    return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Suas peças"))
        .getByRole(
            AriaRole.BUTTON,
            new Locator.GetByRoleOptions().setName(Pattern.compile("^" + literal(peca.nome))));
  }

  Locator lugarLivre(Peca peca, Lugar lugar) {
    return botao("Colocar " + peca.artigo + ": " + lugar.nome);
  }

  Locator lugarOcupado(Lugar lugar, String dono) {
    return botao(lugar.nome + ", ocupado por " + dono);
  }

  /** A peça desenhada na cena, no lugar dado (vale para as próprias e as do parceiro). */
  Locator pecaNoTerraco(Peca peca, String dono, Lugar lugar) {
    return page.getByRole(
        AriaRole.BUTTON,
        new Page.GetByRoleOptions()
            .setName(
                Pattern.compile(
                    "^" + literal(peca.nome + " de " + dono + ", " + lugar.nome) + "(\\.|$)")));
  }

  Locator pecaDeNoTerraco(Peca peca, String dono) {
    return page.getByRole(
        AriaRole.BUTTON,
        new Page.GetByRoleOptions()
            .setName(Pattern.compile("^" + literal(peca.nome + " de " + dono + ", "))));
  }

  /** Escolhe a peça no inventário e depois o lugar na cena; espera a cena confirmar. */
  void colocar(Peca peca, Lugar lugar, String dono) {
    selecionar(peca);
    lugarLivre(peca, lugar).click();
    assertThat(pecaNoTerraco(peca, dono, lugar)).isVisible();
  }

  void selecionar(Peca peca) {
    itemDoInventario(peca).click();
    assertThat(itemDoInventario(peca)).hasAttribute("aria-pressed", "true");
  }

  Locator botaoPronto() {
    return botao("Pronto para olhar o céu");
  }

  Locator botaoVoltarAAjustar() {
    return botao("Voltar a ajustar");
  }

  void confirmar() {
    botaoPronto().click();
    assertThat(botaoVoltarAAjustar()).isVisible();
  }

  Locator app() {
    return page.locator(".app");
  }

  Locator tituloDoResultado() {
    return page.locator("#resultado-titulo");
  }

  Locator aviso() {
    return page.getByRole(AriaRole.STATUS);
  }

  Locator tituloEncerrado() {
    return page.getByRole(
        AriaRole.HEADING, new Page.GetByRoleOptions().setName("Terraço encerrado"));
  }

  Locator botaoSair() {
    return botao("Sair");
  }

  Locator botaoContinuarNoTerraco() {
    return botao("Continuar no terraço");
  }

  /** O bloco que pede confirmação antes de encerrar; o nome vem do texto do aviso. */
  Locator confirmacaoDeSaida() {
    return page.getByRole(
        AriaRole.GROUP,
        new Page.GetByRoleOptions().setName(Pattern.compile("^Isso encerra o terraço")));
  }

  /**
   * "Sair" e depois a confirmação: "Encerrar para os dois" com par, "Encerrar o terraço" enquanto
   * espera o convite.
   */
  void sairDoTerraco() {
    botaoSair().click();
    page.getByRole(
            AriaRole.BUTTON,
            new Page.GetByRoleOptions()
                .setName(Pattern.compile("^Encerrar (para os dois|o terraço)$")))
        .click();
  }

  /** Só Tab: avança o foco até o alvo, com limite para não girar para sempre. */
  void tabularAte(Locator alvo) {
    for (int i = 0; i < 40; i++) {
      page.keyboard().press(Navegador.TAB);
      if (alvo.evaluate("el => el === document.activeElement").equals(Boolean.TRUE)) {
        return;
      }
    }
    throw new AssertionError("O Tab não chegou ao alvo em 40 passos.");
  }

  Locator botao(String nome) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }

  /** Escapa para regex JS: o Playwright repassa o padrão ao navegador, que não entende \Q…\E. */
  private static String literal(String texto) {
    return texto.replaceAll("[\\\\^$.|?*+()\\[\\]{}]", "\\\\$0");
  }

  /** Captura para inspeção visual nos dois tamanhos; não é assertion. */
  void capturar(String nome) {
    var original = page.viewportSize();
    for (int[] tamanho : TAMANHOS) {
      page.setViewportSize(tamanho[0], tamanho[1]);
      page.screenshot(
          new Page.ScreenshotOptions()
              .setFullPage(true)
              .setPath(CAPTURAS.resolve(nome + "-" + tamanho[0] + "x" + tamanho[1] + ".png")));
    }
    page.setViewportSize(original.width, original.height);
  }
}
