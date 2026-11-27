package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Minha conta de ponta a ponta, com uma conta criada pela jornada de verdade (link mágico lido do
 * {@code EmailNoLog}, Turnstile do {@link TurnstileFalso}): sair, excluir e desistir de excluir.
 */
class MinhaContaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  @Test
  void sairEncerraSessao(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    abrirMinhaConta(conta, emailNovo());

    botao(conta.page(), "Sair da conta").click();

    assertThat(conta.page()).hasURL(raiz() + "entrar");
    assertThat(heading(conta.page(), "Entrar no Duora")).isVisible();
    conta.abrir(raiz() + "conta");
    assertThat(conta.page()).hasURL(raiz() + "entrar");
  }

  /**
   * A exclusão voluntária libera o e-mail: entrar com ele de novo abre uma conta nova. O segundo
   * {@code linkRecebido} conta com a revisão da F10, que devolve o link mais novo ainda não
   * devolvido para o endereço.
   */
  @Test
  void excluirApagaConta(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    String email = emailNovo();
    abrirMinhaConta(conta, email);

    botaoExcluirDaSecao(conta.page()).click();
    dialogoDeExclusao(conta.page())
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Excluir conta"))
        .click();

    assertThat(heading(conta.page(), "Conta excluída")).isVisible();
    conta.abrir(raiz() + "entrar").pedirLink(email);
    conta.abrirLink(linkRecebido(email));
    conta.confirmarEntrada();
    assertThat(conta.page()).hasURL(raiz() + "cadastro");
    assertThat(heading(conta.page(), "Falta pouco")).isVisible();
  }

  @Test
  void cancelarMantemAConta(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    abrirMinhaConta(conta, emailNovo());

    botaoExcluirDaSecao(conta.page()).click();
    Locator dialogo = dialogoDeExclusao(conta.page());
    dialogo.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Cancelar")).click();

    assertThat(dialogo).isHidden();
    conta.page().reload();
    assertThat(conta.page()).hasURL(raiz() + "conta");
    assertThat(heading(conta.page(), "Minha conta")).isVisible();
  }

  /** Cadastra uma conta nova com o e-mail e abre Minha conta com a sessão que a jornada abriu. */
  private void abrirMinhaConta(ContaPagina conta, String email) {
    conta.cadastrarContaNova(raiz(), email, NASCIMENTO_DE_ADULTO, this::linkRecebido);
    conta.abrir(raiz() + "conta");
    assertThat(heading(conta.page(), "Minha conta")).isVisible();
  }

  private static ContaPagina novaConta(Participantes participantes) {
    TerracoPagina pagina = participantes.novo();
    TurnstileFalso.instalar(pagina.page());
    return ContaPagina.de(pagina);
  }

  private static String emailNovo() {
    return "minha-conta-" + UUID.randomUUID() + "@exemplo.com";
  }

  private static Locator dialogoDeExclusao(Page page) {
    return page.getByRole(
        AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Excluir sua conta?"));
  }

  /** O botão da seção; com o diálogo aberto há outro com o mesmo nome. */
  private static Locator botaoExcluirDaSecao(Page page) {
    return page.getByRole(AriaRole.REGION, new Page.GetByRoleOptions().setName("Excluir conta"))
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Excluir conta"));
  }

  private static Locator botao(Page page, String nome) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }

  private static Locator heading(Page page, String nome) {
    return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(nome));
  }
}
