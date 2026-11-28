package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import bipotheking.net.duora.perfil.FilaDeFotos;
import bipotheking.net.duora.perfil.FotoPendente;
import bipotheking.net.duora.perfil.FotosDeTeste;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A prova da Etapa 3: quem não é o titular vê a silhueta ou a foto borrada, nunca a nítida; o
 * titular vê a nítida, já com o EXIF aplicado. Servidor, banco e armazém de imagens de verdade; só
 * a moderação é feita pelo teste, direto na {@link FilaDeFotos}.
 */
class PerfilIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  /** O maior lote da fila: outras jornadas podem ter deixado pendentes mais antigas. */
  private static final int LOTE_DA_FILA = 50;

  @Autowired private ContasDeTeste contas;
  @Autowired private FilaDeFotos fila;
  @Autowired private FotosDeTeste fotos;

  @Test
  void perfilBorradoParaTerceirosENitidoParaOTitular(Participantes participantes) {
    ContaDeTeste contaDaAna = contas.criarVerificada();
    PerfilPagina ana = entrar(participantes, contaDaAna);
    ana.abrirMeuPerfil(raiz());
    ana.preencherPerfil("Ana", Set.of("Amizade"), "Gosto de céu limpo.");
    ana.salvarPerfil();
    ana.escolherFoto(retrato());
    assertThat(ana.previa()).hasAttribute("src", Pattern.compile("^data:image/"));
    ana.enviarFoto();
    assertThat(ana.page().getByText("Aguardando aprovação. Até lá, os outros veem uma silhueta."))
        .isVisible();

    PerfilPagina bia = entrar(participantes, contas.criarAtiva());
    bia.abrirPerfilDe(raiz(), contaDaAna.id());
    assertThat(titulo(bia.page())).hasText("Ana");
    assertThat(bia.silhueta()).isVisible();
    assertThat(bia.page().locator("img[src*=\"/api/fotos/\"]")).hasCount(0);

    aprovarAPendenteDe(contaDaAna);

    bia.page().reload();
    Locator borrada = bia.foto("Ana");
    assertThat(borrada).hasAttribute("src", Pattern.compile("/borrada$"));
    assertThat(PerfilPagina.dimensoes(borrada)[0]).isPositive();
    String nitidaDaAna =
        Objects.requireNonNull(borrada.getAttribute("src")).replaceFirst("/borrada$", "/nitida");
    assertThat(bia.page().request().get(absoluta(nitidaDaAna)).status()).isEqualTo(404);
    assertThat(bia.page().getByText("Verificado", new Page.GetByTextOptions().setExact(true)))
        .isVisible();

    ana.page().reload();
    Locator nitida = ana.foto("Ana");
    assertThat(nitida).hasAttribute("src", Pattern.compile("/nitida$"));
    int[] dimensoes = PerfilPagina.dimensoes(nitida);
    assertThat(dimensoes[1]).isGreaterThan(dimensoes[0]);

    assertThat(PerfilPagina.violacoesDeCsp(ana.page())).isEmpty();
    assertThat(PerfilPagina.violacoesDeCsp(bia.page())).isEmpty();
  }

  /**
   * Dois aparelhos da mesma conta: o segundo a salvar recebe o conflito e vê o que o primeiro
   * gravou.
   */
  @Test
  void versaoObsoletaRecarregaNoSegundoAparelho(Participantes participantes) {
    ContaDeTeste conta = contas.criar("Ana", NASCIMENTO_DE_ADULTO);
    PerfilPagina celular = entrar(participantes, conta).abrirMeuPerfil(raiz());
    PerfilPagina computador = entrar(participantes, conta).abrirMeuPerfil(raiz());

    celular.preencherPerfil("Ana", Set.of("Amizade"), "um");
    celular.salvarPerfil();
    computador.preencherPerfil("Ana", Set.of("Amizade"), "dois");
    computador
        .page()
        .getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Salvar perfil"))
        .click();

    assertThat(computador.page().getByRole(AriaRole.ALERT))
        .hasText("Seu perfil mudou em outro aparelho. Confira e salve de novo.");
    assertThat(
            computador
                .page()
                .getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Sobre você")))
        .hasValue("um");
  }

  /**
   * A nítida sai com {@code no-store}: depois de a Ana sair, a Bia, no mesmo navegador, pede o
   * endereço da nítida e recebe o 404 do servidor, não a cópia que a Ana carregou. O pedido é um
   * {@code fetch} da própria página, que passa pelo cache HTTP do navegador; a navegação direta não
   * serve, porque o Firefox aborta a carga de um 404 em vez de devolvê-lo.
   */
  @Test
  void trocaDeContaNoMesmoContextoNaoServeANitidaDoCache(Participantes participantes) {
    ContaDeTeste contaDaAna = contas.criar("Ana", NASCIMENTO_DE_ADULTO);
    fotos.enviarAprovada(contaDaAna.id());
    TerracoPagina navegador = Contas.comConta(participantes.novo(), contaDaAna, raiz());
    PerfilPagina ana = PerfilPagina.de(navegador).abrirMeuPerfil(raiz());
    Locator nitida = ana.foto("Ana");
    assertThat(nitida).hasAttribute("src", Pattern.compile("/nitida$"));
    assertThat(PerfilPagina.dimensoes(nitida)[0]).isPositive();
    String enderecoDaNitida = absoluta(Objects.requireNonNull(nitida.getAttribute("src")));

    Page page = ana.page();
    page.navigate(raiz() + "conta");
    page.getByRole(
            AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sair da conta").setExact(true))
        .click();
    assertThat(page).hasURL(raiz() + "entrar");
    Contas.comConta(navegador, contas.criarAtiva(), raiz());

    Object status = page.evaluate("async (url) => (await fetch(url)).status", enderecoDaNitida);
    assertThat(status).isEqualTo(404);
  }

  private PerfilPagina entrar(Participantes participantes, ContaDeTeste conta) {
    return PerfilPagina.de(Contas.comConta(participantes.novo(), conta, raiz()));
  }

  private void aprovarAPendenteDe(ContaDeTeste conta) {
    FotoPendente pendente =
        fila.pendentes(LOTE_DA_FILA).stream()
            .filter(foto -> foto.conta().equals(conta.id()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("a foto enviada não está na fila"));
    fila.aprovar(pendente.id());
  }

  private String absoluta(String caminho) {
    return raiz() + caminho.substring(1);
  }

  private static Locator titulo(Page page) {
    return page.getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setLevel(1));
  }

  private static Path retrato() {
    URL recurso =
        Objects.requireNonNull(
            PerfilIT.class.getResource("/imagens/" + FotosDeTeste.RETRATO), FotosDeTeste.RETRATO);
    try {
      return Path.of(recurso.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}
