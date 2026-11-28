package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Page Object das telas de perfil (Meu perfil, Preferências, Namoro, Orientação e o perfil de outra
 * pessoa): fala com a página só por papéis, rótulos e textos.
 */
final class PerfilPagina {

  /**
   * Guarda cada violação de CSP como uma linha "diretiva endereço" no {@code sessionStorage}: a
   * lista sobrevive às recargas e navegações da mesma aba, que um teste faz várias vezes.
   */
  private static final String GRAVAR_VIOLACOES =
      """
      (() => {
        document.addEventListener("securitypolicyviolation", (evento) => {
          try {
            const anteriores = sessionStorage.getItem("violacoesDeCsp") ?? "";
            const linha = evento.effectiveDirective + " " + evento.blockedURI;
            sessionStorage.setItem("violacoesDeCsp", anteriores + linha + "\\n");
          } catch {
            // about:blank não tem sessionStorage.
          }
        });
      })();
      """;

  private static final String LER_VIOLACOES =
      "() => { try { return sessionStorage.getItem('violacoesDeCsp') ?? ''; } catch { return ''; } }";

  /** As dimensões naturais da imagem, esperando ela terminar de carregar (ou falhar). */
  private static final String DIMENSOES =
      """
      (img) => new Promise((pronto) => {
        const fim = () => pronto([img.naturalWidth, img.naturalHeight]);
        if (img.complete) {
          fim();
        } else {
          img.addEventListener("load", fim, { once: true });
          img.addEventListener("error", fim, { once: true });
        }
      })
      """;

  private static final List<String> INTENCOES = List.of("Amizade", "Namoro");

  private final Page page;

  private PerfilPagina(Page page) {
    this.page = page;
  }

  /** Liga o registro das violações de CSP; chame antes da primeira navegação da página. */
  static PerfilPagina de(TerracoPagina pagina) {
    Page page = pagina.page();
    page.addInitScript(GRAVAR_VIOLACOES);
    return new PerfilPagina(page);
  }

  Page page() {
    return page;
  }

  PerfilPagina abrirMeuPerfil(String raiz) {
    return abrir(raiz + "perfil", "Meu perfil");
  }

  PerfilPagina abrirPreferencias(String raiz) {
    return abrir(raiz + "perfil/preferencias", "Preferências de encontro");
  }

  PerfilPagina abrirNamoro(String raiz) {
    return abrir(raiz + "perfil/namoro", "Para namoro");
  }

  PerfilPagina abrirOrientacao(String raiz) {
    return abrir(raiz + "perfil/orientacao", "Orientação");
  }

  /** O perfil público de outra conta; o título é o apelido, ou "Perfil indisponível". */
  PerfilPagina abrirPerfilDe(String raiz, ContaId conta) {
    page.navigate(raiz + "perfis/" + conta.valor());
    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1)))
        .isVisible();
    return this;
  }

  /** Apelido, intenções (marca as do conjunto e desmarca as outras) e bio. */
  void preencherPerfil(String apelido, Set<String> intencoes, String bio) {
    caixaDeTexto("Apelido").fill(apelido);
    for (String intencao : INTENCOES) {
      caixa(intencao).setChecked(intencoes.contains(intencao));
    }
    caixaDeTexto("Sobre você").fill(bio);
  }

  void escolherPronomes(String rotulo) {
    opcao(rotulo).check();
  }

  void salvarPerfil() {
    botao("Salvar perfil").click();
    assertThat(page.getByText("Perfil salvo.")).isVisible();
  }

  /** Escolhe o arquivo; a tela mostra a prévia e o botão de enviar. */
  void escolherFoto(Path arquivo) {
    page.getByLabel("Escolher foto").setInputFiles(arquivo);
  }

  void enviarFoto() {
    botao("Enviar foto").click();
    assertThat(page.getByText("Foto enviada. Ela aparece depois da aprovação.")).isVisible();
  }

  /** A prévia do arquivo escolhido, antes do envio. */
  Locator previa() {
    return page.getByRole(
        AriaRole.IMG, new Page.GetByRoleOptions().setName("Prévia da foto escolhida"));
  }

  /**
   * A primeira foto com o nome {@code Foto de <apelido>}. Em Meu perfil, é a da seção Foto (a
   * nítida); o cartão "Como os outros veem você" vem depois, com a borrada.
   */
  Locator foto(String apelido) {
    return page.getByRole(
            AriaRole.IMG, new Page.GetByRoleOptions().setName("Foto de " + apelido).setExact(true))
        .first();
  }

  Locator silhueta() {
    return page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("Sem foto"));
  }

  /** Só o clique: a resposta do navegador (permissão dada ou negada) fica para o teste conferir. */
  void usarMinhaLocalizacao() {
    botao("Usar minha localização").click();
  }

  void escolherDistancia(String rotulo) {
    opcao(rotulo).check();
  }

  void escolherUsoDaDistancia(String rotulo) {
    opcao(rotulo).check();
  }

  void salvarPreferencias() {
    botao("Salvar preferências").click();
    assertThat(page.getByText("Preferências salvas.")).isVisible();
  }

  /** O gênero (rádio) e quem conhecer (caixas), pelos rótulos da tela. */
  void preencherNamoro(String genero, Set<String> interesses) {
    opcao(genero).check();
    for (String interesse : interesses) {
      caixa(interesse).check();
    }
  }

  /** Marca a caixa pelo rótulo: consentimento, ou "Mostrar para minhas conexões". */
  void consentir(String rotulo) {
    caixa(rotulo).check();
  }

  void salvarNamoro() {
    botao("Salvar dados de namoro").click();
    assertThat(page.getByText("Dados de namoro salvos.")).isVisible();
  }

  void escolherOrientacao(String rotulo) {
    opcao(rotulo).check();
  }

  void salvarOrientacao() {
    botao("Salvar orientação").click();
    assertThat(page.getByText("Orientação salva.")).isVisible();
  }

  void pausar() {
    botao("Pausar").click();
    assertThat(page.getByText("Sua conta está pausada.")).isVisible();
  }

  void voltarAAparecer() {
    botao("Voltar a aparecer").click();
    assertThat(botao("Pausar")).isVisible();
  }

  /** As violações de CSP desde que {@link #de} ligou o registro, nesta aba. */
  static List<String> violacoesDeCsp(Page page) {
    String linhas = (String) page.evaluate(LER_VIOLACOES);
    return Arrays.stream(linhas.split("\n")).filter(linha -> !linha.isBlank()).toList();
  }

  /**
   * Largura e altura naturais da imagem. Rola até ela antes: a foto fora da tela é {@code lazy} e
   * só carrega ao chegar perto da rolagem.
   */
  static int[] dimensoes(Locator imagem) {
    imagem.scrollIntoViewIfNeeded();
    List<?> medidas = (List<?>) imagem.evaluate(DIMENSOES);
    return new int[] {((Number) medidas.get(0)).intValue(), ((Number) medidas.get(1)).intValue()};
  }

  private PerfilPagina abrir(String url, String titulo) {
    page.navigate(url);
    assertThat(
            page.getByRole(
                AriaRole.HEADING,
                new Page.GetByRoleOptions().setName(titulo).setExact(true).setLevel(1)))
        .isVisible();
    return this;
  }

  private Locator caixaDeTexto(String rotulo) {
    return page.getByRole(
        AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName(rotulo).setExact(true));
  }

  private Locator caixa(String rotulo) {
    return page.getByRole(
        AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName(rotulo).setExact(true));
  }

  private Locator opcao(String rotulo) {
    return page.getByRole(
        AriaRole.RADIO, new Page.GetByRoleOptions().setName(rotulo).setExact(true));
  }

  private Locator botao(String rotulo) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(rotulo).setExact(true));
  }
}
