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
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Varredura do axe-core ({@link Axe}) em cada estado de Meu perfil, um teste por estado. O {@code
 * /api/perfil}, a conta e a foto vêm do Playwright: a tela não depende do back do perfil nem do
 * armazém de imagens.
 */
class AxeMeuPerfilIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  private static final String PERFIL_NAO_SALVO =
      """
      {"versao":null,"apelido":null,"pronomes":null,"descricaoPronomes":null,"intencoes":[],\
      "bio":"","pausada":false,"prontoParaFila":false,"pendencias":["PERFIL"],\
      "foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  /** Com foto aprovada, pronomes e Namoro: o cartão completo. */
  private static final String PERFIL_COMPLETO =
      """
      {"versao":3,"apelido":"Lua","pronomes":"ELU_DELU","descricaoPronomes":null,\
      "intencoes":["AMIZADE","NAMORO"],"bio":"Gosto de céu.","pausada":false,\
      "prontoParaFila":true,"pendencias":[],\
      "foto":{"aprovada":{"nitida":"/api/fotos/a/nitida","borrada":"/api/fotos/a/borrada"},\
      "pendente":null,"recusadaEm":null}}\
      """;

  /** Sem foto, sem pronomes: o cartão com a silhueta e só o apelido. */
  private static final String PERFIL_MINIMO =
      """
      {"versao":2,"apelido":"Sol","pronomes":null,"descricaoPronomes":null,\
      "intencoes":["AMIZADE"],"bio":"","pausada":false,"prontoParaFila":true,"pendencias":[],\
      "foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  private static final String CORPO_DO_CONFLITO =
      """
      {"type":"about:blank","title":"Conflito","status":409,\
      "codigo":"VERSAO_OBSOLETA",\
      "mensagem":"Seu perfil mudou enquanto você editava. Confira e salve de novo."}\
      """;

  /** PNG de 1×1: a foto abre e o cartão mostra a imagem, não a silhueta. */
  private static final byte[] FOTO =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

  @Autowired private ContasDeTeste contas;

  @Test
  void perfilNaoSalvo(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_NAO_SALVO, false);

    assertThat(conta.page().getByText("Seu perfil é o que as pessoas veem quando encontram você."))
        .isVisible();
    assertSemViolacoes(conta.page(), "meu perfil: não salvo");
  }

  @Test
  void cartaoComFotoPronomesESelo(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_COMPLETO, true);

    Locator cartao = cartao(conta.page());
    assertThat(
            cartao.getByRole(AriaRole.IMG, new Locator.GetByRoleOptions().setName("Foto de Lua")))
        .isVisible();
    assertThat(cartao.getByText("elu/delu")).isVisible();
    assertThat(cartao.getByText("Verificado")).isVisible();
    assertSemViolacoes(conta.page(), "meu perfil: cartão com foto, pronomes e selo");
  }

  @Test
  void cartaoSemFotoSemPronomesSemSelo(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_MINIMO, false);

    Locator cartao = cartao(conta.page());
    assertThat(cartao.getByRole(AriaRole.IMG, new Locator.GetByRoleOptions().setName("Sem foto")))
        .isVisible();
    assertThat(cartao.getByText("Verificado")).hasCount(0);
    assertSemViolacoes(conta.page(), "meu perfil: cartão sem foto, pronomes nem selo");
  }

  /** Prefiro descrever aberto e os erros da validação nos três campos. */
  @Test
  void errosDaValidacao(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_MINIMO, false);
    Page page = conta.page();

    page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Apelido")).fill("");
    page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName("Prefiro descrever"))
        .check();
    page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Amizade")).uncheck();
    salvar(page);

    assertThat(page.getByText("Escreva um apelido.")).isVisible();
    assertThat(page.getByText("Escreva seus pronomes com até 20 caracteres.")).isVisible();
    assertThat(page.getByText("Marque Amizade, Namoro ou as duas.")).isVisible();
    assertSemViolacoes(page, "meu perfil: erros da validação");
  }

  @Test
  void avisoAoTirarNamoro(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_COMPLETO, true);
    Page page = conta.page();

    page.getByRole(AriaRole.CHECKBOX, new Page.GetByRoleOptions().setName("Namoro")).uncheck();

    assertThat(
            page.getByText("Ao salvar sem Namoro, apagamos seu gênero e quem você quer conhecer."))
        .isVisible();
    assertSemViolacoes(page, "meu perfil: aviso ao tirar Namoro");
  }

  @Test
  void perfilSalvo(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_MINIMO, false);
    Page page = conta.page();

    salvar(page);

    assertThat(page.getByText("Perfil salvo.")).isVisible();
    assertSemViolacoes(page, "meu perfil: salvo");
  }

  @Test
  void versaoObsoleta(Participantes participantes) {
    ContaPagina conta = meuPerfil(participantes, PERFIL_MINIMO, false);
    Page page = conta.page();
    page.route(
        "**/api/perfil",
        rota -> {
          if ("PUT".equals(rota.request().method())) {
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(409)
                    .setContentType("application/problem+json")
                    .setBody(CORPO_DO_CONFLITO));
          } else {
            rota.fallback();
          }
        });

    salvar(page);

    assertThat(page.getByRole(AriaRole.ALERT))
        .hasText("Seu perfil mudou em outro aparelho. Confira e salve de novo.");
    assertSemViolacoes(page, "meu perfil: versão obsoleta");
  }

  /**
   * Abre Meu perfil com o perfil, a conta (verificada ou não) e a foto respondidos pelo Playwright.
   * O PUT devolve o próprio perfil, como um salvamento sem mudança.
   */
  private ContaPagina meuPerfil(Participantes participantes, String perfil, boolean verificada) {
    ContaPagina conta =
        ContaPagina.de(
            Contas.comConta(
                participantes.novo(),
                contas.criar(EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO),
                raiz()));
    Page page = conta.page();
    page.route("**/api/perfil", rota -> json(rota, perfil));
    page.route(
        "**/api/conta",
        rota ->
            json(
                rota,
                "{\"estado\":\"ATIVA\",\"cadastroConcluido\":true,\"verificada\":"
                    + verificada
                    + "}"));
    page.route(
        "**/api/fotos/**",
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("image/png")
                    .setBodyBytes(FOTO)));
    conta.abrir(raiz() + "perfil");
    assertThat(conta.heading("Meu perfil")).isVisible();
    return conta;
  }

  private static void json(Route rota, String corpo) {
    rota.fulfill(
        new Route.FulfillOptions()
            .setStatus(200)
            .setContentType("application/json")
            .setBody(corpo));
  }

  private static Locator cartao(Page page) {
    return page.getByRole(
        AriaRole.REGION, new Page.GetByRoleOptions().setName("Como os outros veem você"));
  }

  private static void salvar(Page page) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salvar perfil")).click();
  }
}
