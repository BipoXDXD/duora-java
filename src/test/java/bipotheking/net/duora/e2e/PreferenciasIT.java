package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Preferências de encontro e Namoro contra o servidor de verdade: a localização pelo navegador
 * (dada ou negada), o raio que fica gravado e os dados de namoro apagados ao sair de Namoro.
 */
class PreferenciasIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  /** Centro de São Paulo. */
  private static final double LATITUDE = -23.5505;

  private static final double LONGITUDE = -46.6333;

  /** Geolocation que nega a permissão na hora, posto antes de qualquer script da página. */
  private static final String NEGAR =
      """
      Object.defineProperty(navigator, "geolocation", { configurable: true, value: {
        getCurrentPosition: (ok, falha) => falha({ code: 1, PERMISSION_DENIED: 1 }),
      } });
      """;

  /**
   * Geolocation que nunca responde: é o "Agora não" do Firefox, que fecha o pedido sem chamar
   * nenhum callback (o mesmo de ignorar o pedido na barra do navegador).
   */
  private static final String IGNORAR =
      """
      Object.defineProperty(navigator, "geolocation", { configurable: true, value: {
        getCurrentPosition: () => {},
      } });
      """;

  /** Um pouco mais que o VIGIA_DA_PERMISSAO_MS (20 s) do front. */
  private static final long ALEM_DA_VIGIA_MS = 21_000;

  @Autowired private ContasDeTeste contas;

  @Test
  void ativarLocalizacaoEUsarRaio(Participantes participantes) {
    TerracoPagina navegador =
        participantes.novo(
            new Browser.NewContextOptions()
                .setGeolocation(LATITUDE, LONGITUDE)
                .setPermissions(List.of("geolocation")));
    PerfilPagina pagina =
        PerfilPagina.de(
            Contas.comConta(navegador, contas.criar("Ana", NASCIMENTO_DE_ADULTO), raiz()));
    pagina.abrirPreferencias(raiz());

    pagina.usarMinhaLocalizacao();
    assertThat(pagina.page().getByText("Localização ativa (área aproximada)")).isVisible();
    pagina.escolherDistancia("Até 20 km");
    pagina.escolherUsoDaDistancia("Só pessoas dentro do raio");
    pagina.salvarPreferencias();
    pagina.page().reload();

    assertThat(opcao(pagina.page(), "Até 20 km")).isChecked();
    assertThat(opcao(pagina.page(), "Só pessoas dentro do raio")).isChecked();
  }

  @Test
  void localizacaoNegadaVoltaParaSemLimite(Participantes participantes) {
    PerfilPagina pagina =
        PerfilPagina.de(
            Contas.comConta(
                participantes.novo(page -> page.addInitScript(NEGAR)),
                contas.criar("Ana", NASCIMENTO_DE_ADULTO),
                raiz()));
    pagina.abrirPreferencias(raiz());

    pagina.usarMinhaLocalizacao();

    assertThat(
            pagina
                .page()
                .getByText(
                    "Para tentar de novo, libere a localização nas configurações do navegador."))
        .isVisible();
    assertThat(opcao(pagina.page(), "Sem limite")).isChecked();
    assertThat(opcao(pagina.page(), "Até 5 km")).hasAttribute("aria-disabled", "true");
  }

  @Test
  void pedidoIgnoradoDesisteEVoltaParaSemLimite(Participantes participantes) {
    PerfilPagina pagina =
        PerfilPagina.de(
            Contas.comConta(
                participantes.novo(page -> {
                  page.clock().install();
                  page.addInitScript(IGNORAR);
                }),
                contas.criar("Ana", NASCIMENTO_DE_ADULTO),
                raiz()));
    pagina.abrirPreferencias(raiz());

    pagina.usarMinhaLocalizacao();
    assertThat(pagina.page().getByText("Esperando a permissão do navegador…")).isVisible();
    pagina.page().clock().fastForward(ALEM_DA_VIGIA_MS);

    assertThat(
            pagina
                .page()
                .getByText("Não conseguimos sua localização. A distância fica em \"Sem limite\"."))
        .isVisible();
    assertThat(opcao(pagina.page(), "Sem limite")).isChecked();
    assertThat(
            pagina
                .page()
                .getByRole(
                    AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Usar minha localização")))
        .isEnabled();
  }

  @Test
  void namoroGravadoEApagadoAoMudarParaAmizade(Participantes participantes) {
    ContaDeTeste conta = contas.criarVerificada();
    PerfilPagina pagina = PerfilPagina.de(Contas.comConta(participantes.novo(), conta, raiz()));
    pagina.abrirMeuPerfil(raiz());
    pagina.preencherPerfil("Ana", Set.of("Amizade", "Namoro"), "");
    pagina.salvarPerfil();

    pagina.abrirNamoro(raiz());
    pagina.preencherNamoro("Mulher", Set.of("Homens", "Pessoas não binárias"));
    pagina.consentir("Concordo com o uso desses dados para o pareamento");
    pagina.salvarNamoro();
    assertThat(namoroGravado(pagina.page())).contains("\"genero\":\"MULHER\"");

    pagina.abrirMeuPerfil(raiz());
    pagina.preencherPerfil("Ana", Set.of("Amizade"), "");
    pagina.salvarPerfil();
    pagina.abrirNamoro(raiz());

    assertThat(
            pagina
                .page()
                .getByText("O namoro está desligado. Para usar, marque Namoro em Meu perfil."))
        .isVisible();
    assertThat(namoroGravado(pagina.page())).contains("\"genero\":null");
  }

  private String namoroGravado(Page page) {
    return page.request().get(raiz() + "api/perfil/namoro").text();
  }

  private static Locator opcao(Page page, String rotulo) {
    return page.getByRole(
        AriaRole.RADIO, new Page.GetByRoleOptions().setName(rotulo).setExact(true));
  }
}
