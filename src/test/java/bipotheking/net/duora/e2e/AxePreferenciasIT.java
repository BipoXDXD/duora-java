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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Varredura do axe-core ({@link Axe}) em cada estado de Preferências de encontro, um teste por
 * estado. O perfil, as preferências, a localização e a conta vêm do Playwright; o {@code
 * navigator.geolocation} é trocado por um falso depois da carga (a tela o lê só no clique), para o
 * teste não depender da permissão do navegador.
 */
class AxePreferenciasIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  private static final String PERFIL =
      """
      {"versao":2,"apelido":"Sol","pronomes":null,"descricaoPronomes":null,\
      "intencoes":["AMIZADE"],"bio":"","pausada":false,"prontoParaFila":true,"pendencias":[],\
      "foto":{"aprovada":null,"pendente":null,"recusadaEm":null}}\
      """;

  private static final String SEM_LIMITE =
      """
      {"versao":2,"idadeMinima":25,"idadeMaxima":40,"raio":"SEM_LIMITE","raioObrigatorio":false}\
      """;

  private static final String COM_RAIO =
      """
      {"versao":2,"idadeMinima":25,"idadeMaxima":40,"raio":"ATE_20_KM","raioObrigatorio":true}\
      """;

  private static final String INATIVA = "{\"consentida\":false,\"atualizadaEm\":null}";

  private static final String ATIVA =
      "{\"consentida\":true,\"atualizadaEm\":\"2026-11-28T12:00:00Z\"}";

  private static final String CORPO_DO_CONFLITO =
      """
      {"type":"about:blank","title":"Conflito","status":409,\
      "codigo":"VERSAO_OBSOLETA",\
      "mensagem":"Seu perfil mudou enquanto você editava. Confira e salve de novo."}\
      """;

  private static final String CORPO_DA_LOCALIZACAO_NECESSARIA =
      """
      {"type":"about:blank","title":"Regra de negócio","status":422,\
      "codigo":"LOCALIZACAO_NECESSARIA",\
      "mensagem":"Ative a localização para usar um raio."}\
      """;

  /** Geolocation que nega a permissão na hora. */
  private static final String NEGAR =
      """
      () => { Object.defineProperty(navigator, "geolocation", { configurable: true, value: {
        getCurrentPosition: (ok, falha) => falha({ code: 1, PERMISSION_DENIED: 1 }),
      } }); }
      """;

  /** Geolocation que nunca responde: a tela fica esperando a permissão. */
  private static final String NUNCA_RESPONDER =
      """
      () => { Object.defineProperty(navigator, "geolocation", { configurable: true, value: {
        getCurrentPosition: () => {},
      } }); }
      """;

  @Autowired private ContasDeTeste contas;

  @Test
  void semLocalizacao(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, null);

    assertThat(raio(page, "Até 5 km")).hasAttribute("aria-disabled", "true");
    assertThat(page.getByText("Ative a localização para escolher uma distância.")).isVisible();
    assertSemViolacoes(page, "preferências: sem localização");
  }

  @Test
  void localizacaoAtivaComRaio(Participantes participantes) {
    Page page = preferencias(participantes, COM_RAIO, ATIVA, null);

    assertThat(page.getByText("Localização ativa (área aproximada)")).isVisible();
    assertThat(
            page.getByRole(
                AriaRole.GROUP, new Page.GetByRoleOptions().setName("Como usar a distância")))
        .isVisible();
    assertSemViolacoes(page, "preferências: localização ativa com raio");
  }

  @Test
  void esperandoAPermissao(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, NUNCA_RESPONDER);

    usarLocalizacao(page).click();

    assertThat(page.getByText("Esperando a permissão do navegador…")).isVisible();
    assertSemViolacoes(page, "preferências: esperando a permissão");
  }

  @Test
  void localizacaoNegada(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, NEGAR);

    usarLocalizacao(page).click();

    assertThat(
            page.getByText(
                "Para tentar de novo, libere a localização nas configurações do navegador."))
        .isVisible();
    assertSemViolacoes(page, "preferências: localização negada");
  }

  @Test
  void localizacaoDesativada(Participantes participantes) {
    Page page = preferencias(participantes, COM_RAIO, ATIVA, null);
    page.route(
        "**/api/perfil/localizacao",
        rota -> {
          if ("DELETE".equals(rota.request().method())) {
            rota.fulfill(new Route.FulfillOptions().setStatus(204));
          } else {
            rota.fallback();
          }
        });

    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Desativar localização"))
        .click();

    assertThat(page.getByText("Localização desativada. A distância voltou para \"Sem limite\"."))
        .isVisible();
    assertSemViolacoes(page, "preferências: localização desativada");
  }

  @Test
  void idadeRecusada(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, null);

    page.getByRole(AriaRole.SPINBUTTON, new Page.GetByRoleOptions().setName("De")).fill("50");
    salvar(page);

    assertThat(page.getByText("Use idades de 18 a 99, com \"De\" menor ou igual a \"Até\"."))
        .isVisible();
    assertSemViolacoes(page, "preferências: idade recusada");
  }

  @Test
  void preferenciasSalvas(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, null);

    salvar(page);

    assertThat(page.getByText("Preferências salvas.")).isVisible();
    assertSemViolacoes(page, "preferências: salvas");
  }

  @Test
  void versaoObsoleta(Participantes participantes) {
    Page page = preferencias(participantes, SEM_LIMITE, INATIVA, null);
    responderAoPut(page, 409, CORPO_DO_CONFLITO);

    salvar(page);

    assertThat(page.getByRole(AriaRole.ALERT))
        .hasText("Suas preferências mudaram em outro aparelho. Confira e salve de novo.");
    assertSemViolacoes(page, "preferências: versão obsoleta");
  }

  @Test
  void localizacaoNecessaria(Participantes participantes) {
    Page page = preferencias(participantes, COM_RAIO, ATIVA, null);
    responderAoPut(page, 422, CORPO_DA_LOCALIZACAO_NECESSARIA);

    salvar(page);

    assertThat(page.getByText("Ative a localização para usar um raio.")).isVisible();
    assertSemViolacoes(page, "preferências: localização necessária");
  }

  private static Locator raio(Page page, String nome) {
    return page.getByRole(AriaRole.RADIO, new Page.GetByRoleOptions().setName(nome));
  }

  private static Locator usarLocalizacao(Page page) {
    return page.getByRole(
        AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Usar minha localização"));
  }

  private static void salvar(Page page) {
    page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salvar preferências"))
        .click();
  }

  /** O PUT das preferências responde o erro; o GET segue para a rota já preparada. */
  private static void responderAoPut(Page page, int status, String corpo) {
    page.route(
        "**/api/perfil/preferencias",
        rota -> {
          if ("PUT".equals(rota.request().method())) {
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(status)
                    .setContentType("application/problem+json")
                    .setBody(corpo));
          } else {
            rota.fallback();
          }
        });
  }

  /**
   * Abre Preferências com as respostas do Playwright. O PUT das preferências devolve o próprio
   * corpo lido, como um salvamento sem mudança. {@code geolocalizacao}, quando não nulo, roda na
   * página já aberta.
   */
  private Page preferencias(
      Participantes participantes,
      String preferencias,
      String localizacao,
      @Nullable String geolocalizacao) {
    ContaPagina conta =
        ContaPagina.de(
            Contas.comConta(
                participantes.novo(),
                contas.criar(EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO),
                raiz()));
    Page page = conta.page();
    page.route("**/api/perfil", rota -> json(rota, PERFIL));
    page.route("**/api/perfil/preferencias", rota -> json(rota, preferencias));
    page.route("**/api/perfil/localizacao", rota -> json(rota, localizacao));
    page.route(
        "**/api/conta",
        rota ->
            json(rota, "{\"estado\":\"ATIVA\",\"cadastroConcluido\":true,\"verificada\":false}"));
    conta.abrir(raiz() + "perfil/preferencias");
    assertThat(conta.heading("Preferências de encontro")).isVisible();
    if (geolocalizacao != null) {
      page.evaluate(geolocalizacao);
    }
    return page;
  }

  private static void json(Route rota, String corpo) {
    rota.fulfill(
        new Route.FulfillOptions()
            .setStatus(200)
            .setContentType("application/json")
            .setBody(corpo));
  }
}
