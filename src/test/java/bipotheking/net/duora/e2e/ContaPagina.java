package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.function.Function;

/** Page Object das telas de conta: fala com a página só por papéis, rótulos e textos. */
final class ContaPagina {

  private static final String PEDIDO_DE_LINK = "/api/conta/entrar";

  private final Page page;

  ContaPagina(Page page) {
    this.page = page;
  }

  static ContaPagina de(TerracoPagina pagina) {
    return new ContaPagina(pagina.page());
  }

  Page page() {
    return page;
  }

  ContaPagina abrir(String url) {
    page.navigate(url);
    return this;
  }

  /**
   * Preenche o e-mail e envia quando o Turnstile libera o botão; espera e devolve a resposta do
   * servidor, qualquer que seja (o pedido acima do limite volta 429).
   */
  Response pedirLink(String email) {
    page.getByLabel("Seu e-mail").fill(email);
    Locator enviar = botao("Enviar link");
    assertThat(enviar).not().hasAttribute("aria-disabled", "true");
    return page.waitForResponse(
        resposta ->
            resposta.url().endsWith(PEDIDO_DE_LINK) && "POST".equals(resposta.request().method()),
        enviar::click);
  }

  /**
   * Abre o link numa navegação nova, passando por {@code about:blank}. Contorna uma limitação
   * conhecida do front: a tela tira o token da URL na montagem, e um {@code #t=} novo aberto em
   * {@code /entrar/confirmar} na mesma aba muda só o fragmento, sem que a tela o releia. O conserto
   * do front fica para a revisão final da etapa.
   */
  void abrirLink(String link) {
    page.navigate("about:blank");
    page.navigate(link);
  }

  void confirmarEntrada() {
    botao("Entrar").click();
  }

  void concluirCadastro(LocalDate nascimento) {
    page.getByLabel("Data de nascimento").fill(nascimento.toString());
    page.getByRole(
            AriaRole.CHECKBOX,
            new Page.GetByRoleOptions()
                .setName("Li e aceito os Termos de uso e a Política de privacidade"))
        .check();
    botao("Concluir cadastro").click();
  }

  /**
   * A jornada inteira de uma conta nova, do pedido do link à tela de cadastro recebido. O {@code
   * link} entrega o link de entrada recebido pelo e-mail (veja {@code ContaE2eBase#linkRecebido}).
   */
  ContaPagina cadastrarContaNova(
      String raiz, String email, LocalDate nascimento, Function<String, String> link) {
    abrir(raiz + "entrar").pedirLink(email);
    abrirLink(link.apply(email));
    confirmarEntrada();
    concluirCadastro(nascimento);
    assertThat(heading("Cadastro recebido")).isVisible();
    return this;
  }

  Locator heading(String nome) {
    return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(nome));
  }

  Locator link(String nome) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }

  private Locator botao(String nome) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }
}
