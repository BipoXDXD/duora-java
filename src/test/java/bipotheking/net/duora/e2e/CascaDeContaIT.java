package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.Cookie;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * A casca de conta na primeira carga: o Spring encaminha a rota ao index.html, o despacho baixa o
 * chunk {@code casca-de-conta} só fora do terraço sem tocar no fragmento, e a casca lê o token do
 * link mágico e o tira da URL.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class CascaDeContaIT {

  private static final String CHUNK_DA_CASCA = "/app/casca-de-conta";

  /**
   * Caminho que nunca terá tela nem encaminhamento no Spring: só a navegação no cliente chega a
   * ele, e a casca cai na {@code NaoEncontrada}.
   */
  private static final String ROTA_INEXISTENTE = "rota-inexistente";

  @Value("${local.server.port}")
  private int porta;

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  @Autowired private ContasDeTeste contas;

  /** O botão Entrar só aparece com o token em mãos; sem ele, a tela explica que falta o código. */
  @Test
  void linkDeConfirmarEntregaOTokenATelaETiraODaUrl(Participantes participantes) {
    Page page = participantes.novo().page();

    Response chunk =
        page.waitForResponse(
            resposta -> resposta.url().contains(CHUNK_DA_CASCA),
            () -> page.navigate(raiz(porta) + "entrar/confirmar#t=abc"));
    assertThat(chunk.status()).isEqualTo(200);
    assertThat(page.getByRole(AriaRole.MAIN)).isVisible();
    assertThat(page.locator("#raiz")).not().hasAttribute("aria-busy", "true");

    assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar")))
        .isVisible();
    assertThat(page.url()).isEqualTo(raiz(porta) + "entrar/confirmar");
  }

  @Test
  void confirmarEntradaSemViolacoes(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta) + "entrar/confirmar#t=abc").page();

    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Confirmar entrada")))
        .isVisible();

    assertSemViolacoes(page, "confirmar entrada");
  }

  /** Token que nunca existiu: o servidor de verdade responde LINK_INVALIDO, a recusa final. */
  @Test
  void linkInvalidoSemViolacoes(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta) + "entrar/confirmar#t=abc").page();

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Entrar")).click();

    assertThat(page.getByText("Este link expirou ou já foi usado. Peça um novo.")).isFocused();
    assertThat(
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Pedir novo link")))
        .isVisible();

    assertSemViolacoes(page, "link inválido na confirmação");
  }

  /** O MockMvc só registra o forward; aqui o servidor de verdade o executa. */
  @Test
  void rotaEncaminhadaChegaSemCache(Participantes participantes) {
    Response resposta = participantes.novo().page().navigate(raiz(porta) + "conta");

    assertThat(resposta.status()).isEqualTo(200);
    assertThat(resposta.headerValue("cache-control")).isEqualTo("no-cache");
  }

  @Test
  void terracoNaoBaixaOChunkDaCasca(Participantes participantes) {
    List<String> pedidos = new ArrayList<>();
    TerracoPagina ana =
        participantes.novo(page -> page.onRequest(pedido -> pedidos.add(pedido.url())));
    Page page = ana.page();

    page.waitForResponse(
        resposta -> resposta.url().endsWith("/api/sala"), () -> ana.abrir(raiz(porta)));
    assertThat(ana.botao("Criar terraço")).isVisible();

    assertThat(pedidos).isNotEmpty().noneMatch(url -> url.contains(CHUNK_DA_CASCA));
  }

  /**
   * O Spring dá 404 a caminho fora de {@code RotasDoSpa.CAMINHOS}; a {@code NaoEncontrada} aparece
   * quando a navegação no cliente sai das rotas da casca. O {@code createBrowserRouter} escuta o
   * {@code popstate} da história do navegador.
   */
  @Test
  void naoEncontradaSemViolacoes(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta) + "conta").page();
    assertThat(page.getByRole(AriaRole.MAIN)).isVisible();

    page.evaluate(
        "caminho => { history.pushState({}, '', caminho);"
            + " dispatchEvent(new PopStateEvent('popstate')); }",
        "/" + ROTA_INEXISTENTE);

    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Página não encontrada")))
        .isVisible();

    assertSemViolacoes(page, "página não encontrada da casca");
  }

  @Test
  void cadastroSemSessaoVaiParaEntrar(Participantes participantes) {
    Page page = participantes.novo().abrir(raiz(porta) + "cadastro").page();

    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Entrar no Duora")))
        .isVisible();
    assertThat(page).hasURL(raiz(porta) + "entrar");
  }

  @Test
  void cadastroSemViolacoes(Participantes participantes) {
    Page page = logado(participantes, EstadoConta.PENDENTE);
    page.navigate(raiz(porta) + "cadastro");

    assertThat(page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName("Falta pouco")))
        .isVisible();
    assertThat(page.getByLabel("Data de nascimento")).isVisible();

    assertSemViolacoes(page, "cadastro");
  }

  @Test
  void aguardandoSemViolacoes(Participantes participantes) {
    Page page = logado(participantes, EstadoConta.AGUARDANDO_VERIFICACAO);
    page.navigate(raiz(porta) + "aguardando");

    assertThat(
            page.getByRole(
                AriaRole.HEADING, new Page.GetByRoleOptions().setName("Cadastro recebido")))
        .isVisible();

    assertSemViolacoes(page, "aguardando verificação");
  }

  /** A recusa exclui a conta e encerra a sessão: voltar ao cadastro leva a quem não entrou. */
  @Test
  void recusaPorIdadeNaoDeixaSessao(Participantes participantes) {
    Page page = logado(participantes, EstadoConta.PENDENTE);
    page.navigate(raiz(porta) + "cadastro");

    page.getByLabel("Data de nascimento").fill("2015-01-01");
    page.getByRole(
            AriaRole.CHECKBOX,
            new Page.GetByRoleOptions()
                .setName("Li e aceito os Termos de uso e a Política de privacidade"))
        .check();
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Concluir cadastro"))
        .click();

    assertThat(page.getByText("O Duora é só para maiores de 18 anos. Não criamos a conta."))
        .isFocused();
    assertSemViolacoes(page, "recusa por idade");

    page.navigate(raiz(porta) + "cadastro");
    assertThat(page).hasURL(raiz(porta) + "entrar");
  }

  /**
   * Abre uma conta no estado pedido e põe o cookie da sessão dela no navegador. Só a pendente fica
   * sem data de nascimento; o esquema exige a data a partir do cadastro.
   */
  private Page logado(Participantes participantes, EstadoConta estado) {
    ContasDeTeste.ContaDeTeste conta =
        contas.criar(estado, estado == EstadoConta.PENDENTE ? null : NASCIMENTO_DE_ADULTO);
    String[] cookie = conta.cookie().split("=", 2);
    Page page = participantes.novo().page();
    page.context().addCookies(List.of(new Cookie(cookie[0], cookie[1]).setUrl(raiz(porta))));
    return page;
  }
}
