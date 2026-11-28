package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * A pausa da conta e o que outra pessoa vê dos dados opcionais: os pronomes aparecem, a orientação
 * nunca (no máximo vai às conexões, que chegam numa etapa futura).
 */
class OrientacaoEPausaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  @Autowired private ContasDeTeste contas;

  @Test
  void pausarEVoltar(Participantes participantes) {
    PerfilPagina pagina = entrar(participantes, contas.criar("Ana", NASCIMENTO_DE_ADULTO));
    pagina.abrirMeuPerfil(raiz());

    pagina.pausar();
    assertThat(meuPerfil(pagina.page())).contains("\"pausada\":true");

    pagina.voltarAAparecer();
    assertThat(pagina.page().getByText("Sua conta está pausada.")).hasCount(0);
    assertThat(meuPerfil(pagina.page())).contains("\"pausada\":false");
  }

  @Test
  void pronomesAparecemParaTerceiroEOrientacaoNao(Participantes participantes) {
    ContaDeTeste contaDaAna = contas.criarVerificada();
    PerfilPagina ana = entrar(participantes, contaDaAna);
    ana.abrirMeuPerfil(raiz());
    ana.preencherPerfil("Ana", Set.of("Amizade"), "");
    ana.escolherPronomes("elu/delu");
    ana.salvarPerfil();
    ana.abrirOrientacao(raiz());
    ana.escolherOrientacao("Bissexual");
    ana.consentir("Mostrar para minhas conexões");
    ana.consentir("Concordo em guardar minha orientação no Duora");
    ana.salvarOrientacao();

    PerfilPagina bia = entrar(participantes, contas.criarAtiva());
    bia.abrirPerfilDe(raiz(), contaDaAna.id());

    assertThat(bia.page().getByText("elu/delu", new Page.GetByTextOptions().setExact(true)))
        .isVisible();
    assertThat(bia.page().getByText("Bissexual")).hasCount(0);
  }

  private PerfilPagina entrar(Participantes participantes, ContaDeTeste conta) {
    return PerfilPagina.de(Contas.comConta(participantes.novo(), conta, raiz()));
  }

  private String meuPerfil(Page page) {
    return page.request().get(raiz() + "api/perfil").text();
  }
}
