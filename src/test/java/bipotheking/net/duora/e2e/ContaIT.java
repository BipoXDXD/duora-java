package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A jornada de conta de ponta a ponta: pedir o link, abri-lo, entrar e concluir o cadastro, com o
 * e-mail lido do {@code EmailNoLog} e o Turnstile servido pelo {@link TurnstileFalso}.
 */
class ContaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);
  private static final LocalDate NASCIMENTO_DE_MENOR = LocalDate.of(2010, 1, 1);
  private static final String COOKIE_DA_SESSAO = "duora_sessao";

  @Autowired private ContasDeTeste contas;

  @Test
  void cadastroCompletoNoCelular(Participantes participantes) {
    TerracoPagina celular = participantes.novoCelular();
    TurnstileFalso.instalar(celular.page());
    ContaPagina conta = ContaPagina.de(celular);
    String email = emailNovo();

    conta.abrir(raiz() + "entrar").pedirLink(email);
    conta.abrirLink(linkRecebido(email));
    conta.confirmarEntrada();
    conta.concluirCadastro(NASCIMENTO_DE_ADULTO);

    assertThat(conta.heading("Cadastro recebido")).isVisible();
  }

  @Test
  void menorRecusadoNoCadastro(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    String email = emailNovo();

    conta.abrir(raiz() + "entrar").pedirLink(email);
    conta.abrirLink(linkRecebido(email));
    conta.confirmarEntrada();
    conta.concluirCadastro(NASCIMENTO_DE_MENOR);

    assertThat(conta.page().getByText("O Duora é só para maiores de 18 anos. Não criamos a conta."))
        .isVisible();
    conta.abrir(raiz() + "conta");
    assertThat(conta.page()).hasURL(raiz() + "entrar");
  }

  @Test
  void linkUsadoOferecePedirNovo(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    String email = emailNovo();
    conta.abrir(raiz() + "entrar").pedirLink(email);
    String link = linkRecebido(email);
    conta.abrirLink(link);
    conta.confirmarEntrada();
    assertThat(conta.heading("Falta pouco")).isVisible();

    conta.abrirLink(link);
    conta.confirmarEntrada();

    assertThat(
            conta
                .page()
                .getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Pedir novo link")))
        .isVisible();
  }

  /** O helper da base entrega o link de cada pedido, não o do pedido anterior. */
  @Test
  void segundoPedidoTrazLinkNovo(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);
    String email = emailNovo();
    conta.abrir(raiz() + "entrar").pedirLink(email);
    String primeiro = linkRecebido(email);

    conta.pedirLink(email);

    assertThat(linkRecebido(email)).isNotEqualTo(primeiro);
  }

  /** No WebKit a flag de cookie seguro está desligada; quem prova o {@code Secure} é a API (C4). */
  @Test
  void sessaoSobreviveARecarga(Participantes participantes) {
    ContaPagina conta = novaConta(participantes);

    conta.cadastrarContaNova(raiz(), emailNovo(), NASCIMENTO_DE_ADULTO, this::linkRecebido);

    assertThat(nomesDosCookies(conta.page())).contains(COOKIE_DA_SESSAO);
    conta.page().reload();
    assertThat(conta.page()).hasURL(raiz() + "aguardando");
    assertThat(conta.heading("Cadastro recebido")).isVisible();
  }

  @Test
  void topoMostraMinhaContaDepoisDeEntrar(Participantes participantes) {
    TerracoPagina comSessao = Contas.comConta(participantes.novo(), contas.criarAtiva(), raiz());
    TerracoPagina semSessao = participantes.novo();

    comSessao.abrir(raiz());
    semSessao.abrir(raiz());

    assertThat(linkDoTopo(comSessao.page(), "Minha conta")).isVisible();
    assertThat(linkDoTopo(comSessao.page(), "Entrar")).hasCount(0);
    assertThat(linkDoTopo(semSessao.page(), "Entrar")).isVisible();
    assertThat(linkDoTopo(semSessao.page(), "Minha conta")).hasCount(0);
  }

  private static ContaPagina novaConta(Participantes participantes) {
    TerracoPagina pagina = participantes.novo();
    TurnstileFalso.instalar(pagina.page());
    return ContaPagina.de(pagina);
  }

  private static List<String> nomesDosCookies(Page page) {
    return page.context().cookies().stream().map(cookie -> cookie.name).toList();
  }

  private static Locator linkDoTopo(Page page, String nome) {
    return page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }
}
