package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Varredura do axe-core ({@link Axe}) em cada estado novo das telas de conta e das telas públicas,
 * um teste por estado. Os participantes usam movimento reduzido, como o {@link Axe} exige; nas
 * telas que pedem a verificação de segurança, o Turnstile é o {@link TurnstileFalso}.
 */
class AxeContaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  /** Data relativa: uma data fixa deixaria de ser de menor com o passar dos anos. */
  private static final LocalDate NASCIMENTO_DE_MENOR =
      LocalDate.now(ZoneId.of("America/Sao_Paulo")).minusYears(10);

  /** 5 minutos: a tela arredonda o {@code Retry-After} para minutos inteiros. */
  private static final String SEGUNDOS_PARA_TENTAR_DE_NOVO = "300";

  /** O corpo do {@code TratamentoDeErros} para um {@code LimiteExcedido}. */
  private static final String CORPO_DO_LIMITE =
      """
      {"type":"about:blank","title":"Limite de taxa","status":429,\
      "detail":"Muitas tentativas. Tente de novo em alguns minutos.",\
      "instance":"/api/conta/entrar","codigo":"LIMITE_DE_TAXA",\
      "mensagem":"Muitas tentativas. Tente de novo em alguns minutos."}\
      """;

  /** Base64url com o tamanho de um token de 32 bytes, que nunca foi emitido. */
  private static final String TOKEN_INEXISTENTE = "A".repeat(43);

  @Autowired private ContasDeTeste contas;

  @Test
  void entrar(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes).abrir(raiz() + "entrar");

    assertThat(conta.heading("Entrar no Duora")).isVisible();
    assertThat(botao(conta.page(), "Enviar link")).not().hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "entrar");
  }

  /**
   * O multiplicador 1000 dos limites no failsafe não deixa uma rajada chegar ao 429; a rota do
   * Playwright devolve a recusa como o servidor a escreveria.
   */
  @Test
  void entrarComLimite(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes);
    conta
        .page()
        .route(
            "**/api/conta/entrar",
            rota ->
                rota.fulfill(
                    new Route.FulfillOptions()
                        .setStatus(429)
                        .setContentType("application/problem+json")
                        .setHeaders(Map.of("Retry-After", SEGUNDOS_PARA_TENTAR_DE_NOVO))
                        .setBody(CORPO_DO_LIMITE)));

    conta.abrir(raiz() + "entrar").pedirLink(emailNovo());

    assertThat(conta.page().getByText("Muitas tentativas. Tente de novo em 5 minutos."))
        .isVisible();
    assertSemViolacoes(conta.page(), "entrar com limite");
  }

  @Test
  void confirmarEntrada(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    conta.abrirLink(raiz() + "entrar/confirmar#t=" + TOKEN_INEXISTENTE);

    assertThat(conta.heading("Confirmar entrada")).isVisible();

    assertSemViolacoes(conta.page(), "confirmar entrada");
  }

  @Test
  void confirmarComLinkInvalido(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    conta.abrirLink(raiz() + "entrar/confirmar#t=" + TOKEN_INEXISTENTE);

    conta.confirmarEntrada();

    assertThat(conta.page().getByText("Este link expirou ou já foi usado. Peça um novo."))
        .isVisible();
    assertThat(conta.link("Pedir novo link")).isVisible();
    assertSemViolacoes(conta.page(), "confirmar com link inválido");
  }

  @Test
  void cadastro(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.PENDENTE, null).abrir(raiz() + "cadastro");

    assertThat(conta.heading("Falta pouco")).isVisible();
    assertThat(conta.page().getByLabel("Data de nascimento")).isVisible();
    assertThat(conta.page().getByRole(AriaRole.CHECKBOX)).isVisible();

    assertSemViolacoes(conta.page(), "cadastro");
  }

  @Test
  void cadastroComMenorRecusado(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.PENDENTE, null).abrir(raiz() + "cadastro");

    conta.concluirCadastro(NASCIMENTO_DE_MENOR);

    assertThat(conta.heading("Cadastro não concluído")).isVisible();
    assertThat(conta.page().getByText("O Duora é só para maiores de 18 anos. Não criamos a conta."))
        .isVisible();
    assertSemViolacoes(conta.page(), "cadastro com menor recusado");
  }

  @Test
  void aguardando(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO_DE_ADULTO)
            .abrir(raiz() + "aguardando");

    assertThat(conta.heading("Cadastro recebido")).isVisible();

    assertSemViolacoes(conta.page(), "aguardando");
  }

  @Test
  void minhaConta(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);

    assertThat(conta.page().getByText("Falta confirmar sua idade. Essa etapa chega em breve."))
        .isVisible();
    assertSemViolacoes(conta.page(), "minha conta");
  }

  @Test
  void dialogoDeExclusaoAberto(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);

    botaoExcluirDaSecao(conta.page()).click();

    assertThat(
            conta
                .page()
                .getByRole(
                    AriaRole.DIALOG, new Page.GetByRoleOptions().setName("Excluir sua conta?")))
        .isVisible();
    assertSemViolacoes(conta.page(), "diálogo de exclusão aberto");
  }

  @Test
  void contaExcluida(Participantes participantes) {
    ContaPagina conta = minhaContaAberta(participantes);
    botaoExcluirDaSecao(conta.page()).click();

    conta
        .page()
        .getByRole(AriaRole.DIALOG)
        .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Excluir conta"))
        .click();

    assertThat(conta.heading("Conta excluída")).isVisible();
    assertSemViolacoes(conta.page(), "conta excluída");
  }

  /** Com o token do Turnstile em mãos: o botão de envio fica disponível. */
  @Test
  void denunciar(Participantes participantes) {
    ContaPagina conta = comTurnstile(participantes).abrir(raiz() + "denunciar");

    assertThat(conta.heading("Denunciar um problema")).isVisible();
    assertThat(botao(conta.page(), "Enviar denúncia")).not().hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "denunciar");
  }

  @Test
  void termos(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "termos");

    assertThat(conta.heading("Termos de uso").first()).isVisible();

    assertSemViolacoes(conta.page(), "termos");
  }

  @Test
  void privacidade(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "privacidade");

    assertThat(conta.heading("Política de privacidade").first()).isVisible();

    assertSemViolacoes(conta.page(), "privacidade");
  }

  /** Sem o token do Turnstile: a tela espera a verificação com o envio indisponível. */
  @Test
  void denunciarSemToken(Participantes participantes) {
    ContaPagina conta =
        ContaPagina.de(participantes.novo(Cenarios::turnstileSemToken)).abrir(raiz() + "denunciar");

    assertThat(botao(conta.page(), "Enviar denúncia")).hasAttribute("aria-disabled", "true");

    assertSemViolacoes(conta.page(), "denunciar sem token");
  }

  @Test
  void encarregado(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "encarregado");

    assertThat(conta.heading("Encarregado de dados")).isVisible();
    assertThat(conta.link("encarregado@exemplo.invalid")).isVisible();

    assertSemViolacoes(conta.page(), "encarregado");
  }

  /**
   * O Spring dá 404 a caminho fora das rotas do SPA; a página de não encontrada da casca aparece
   * quando a navegação no cliente sai das rotas dela (o router escuta o {@code popstate}).
   */
  @Test
  void paginaNaoEncontrada(Participantes participantes) {
    ContaPagina conta = semConta(participantes).abrir(raiz() + "encarregado");
    assertThat(conta.heading("Encarregado de dados")).isVisible();

    conta
        .page()
        .evaluate(
            "caminho => { history.pushState({}, '', caminho);"
                + " dispatchEvent(new PopStateEvent('popstate')); }",
            "/rota-inexistente");

    assertThat(conta.heading("Página não encontrada")).isVisible();
    assertSemViolacoes(conta.page(), "página não encontrada");
  }

  private static ContaPagina semConta(Participantes participantes) {
    return ContaPagina.de(participantes.novo());
  }

  /** Para as telas que pedem a verificação de segurança antes de enviar. */
  private static ContaPagina comTurnstile(Participantes participantes) {
    ContaPagina conta = semConta(participantes);
    TurnstileFalso.instalar(conta.page());
    return conta;
  }

  /** Conta no estado pedido com o cookie da sessão no navegador, antes de abrir o site. */
  private ContaPagina logado(
      Participantes participantes, EstadoConta estado, @Nullable LocalDate nascimento) {
    return ContaPagina.de(
        Contas.comConta(participantes.novo(), contas.criar(estado, nascimento), raiz()));
  }

  private ContaPagina minhaContaAberta(Participantes participantes) {
    ContaPagina conta =
        logado(participantes, EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO_DE_ADULTO)
            .abrir(raiz() + "conta");
    assertThat(conta.heading("Minha conta")).isVisible();
    return conta;
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
}
