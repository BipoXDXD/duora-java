package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Axe.assertSemViolacoes;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Varredura do axe-core ({@link Axe}) em cada estado do perfil de outra pessoa, um teste por
 * estado. O cartão vem do Playwright (o {@code /api/perfis/<id>} e a foto); o perfil indisponível
 * vem do back de verdade: o 404 de uma conta que não existe e o 400 de um id que não é UUID.
 */
class AxePerfilDeOutraPessoaIT extends ContaE2eBase {

  private static final LocalDate NASCIMENTO_DE_ADULTO = LocalDate.of(1990, 5, 10);

  private static final String OUTRA_CONTA = "0193f1c2-7a4b-7c3d-8e5f-0a1b2c3d4e5f";

  /** Com foto borrada, pronomes e selo: o cartão completo. */
  private static final String PERFIL_COMPLETO =
      """
      {"apelido":"Ana","foto":"/api/fotos/a/borrada","pronomes":"ela/dela","verificado":true}\
      """;

  /** Sem foto, sem pronomes e sem selo: a silhueta e só o apelido. */
  private static final String PERFIL_MINIMO =
      """
      {"apelido":"Sol","foto":null,"pronomes":null,"verificado":false}\
      """;

  /** PNG de 1×1: a foto abre e o cartão mostra a imagem, não a silhueta. */
  private static final byte[] FOTO =
      Base64.getDecoder()
          .decode(
              "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

  @Autowired private ContasDeTeste contas;

  @Test
  void cartaoComFotoPronomesESelo(Participantes participantes) {
    ContaPagina conta = comConta(participantes);
    Page page = conta.page();
    List<String> pedidos = new ArrayList<>();
    page.onRequest(pedido -> pedidos.add(pedido.url()));
    responderPerfil(page, PERFIL_COMPLETO);

    conta.abrir(raiz() + "perfis/" + OUTRA_CONTA);

    assertThat(conta.heading("Ana")).isVisible();
    assertThat(page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("Foto de Ana")))
        .isVisible();
    assertThat(page.getByText("ela/dela")).isVisible();
    assertThat(page.getByText("Verificado")).isVisible();
    assertThat(page).hasTitle("Ana · Duora");
    assertThat(pedidos).noneMatch(url -> url.contains("nitida"));
    assertSemViolacoes(page, "perfil de outra pessoa: cartão com foto, pronomes e selo");
  }

  @Test
  void cartaoSemFotoSemPronomesSemSelo(Participantes participantes) {
    ContaPagina conta = comConta(participantes);
    Page page = conta.page();
    responderPerfil(page, PERFIL_MINIMO);

    conta.abrir(raiz() + "perfis/" + OUTRA_CONTA);

    assertThat(conta.heading("Sol")).isVisible();
    assertThat(page.getByRole(AriaRole.IMG, new Page.GetByRoleOptions().setName("Sem foto")))
        .isVisible();
    assertThat(page.getByText("Verificado")).hasCount(0);
    assertSemViolacoes(page, "perfil de outra pessoa: cartão sem foto, pronomes nem selo");
  }

  @Test
  void perfilInexistente(Participantes participantes) {
    ContaPagina conta = comConta(participantes);

    conta.abrir(raiz() + "perfis/" + UUID.randomUUID());

    assertIndisponivel(conta);
    assertSemViolacoes(conta.page(), "perfil de outra pessoa: inexistente");
  }

  /** O 400 REQUISICAO_INVALIDA do id que não é UUID mostra a mesma tela do 404. */
  @Test
  void idMalformado(Participantes participantes) {
    ContaPagina conta = comConta(participantes);

    conta.abrir(raiz() + "perfis/nao-e-uuid");

    assertIndisponivel(conta);
    assertSemViolacoes(conta.page(), "perfil de outra pessoa: id malformado");
  }

  private static void assertIndisponivel(ContaPagina conta) {
    Page page = conta.page();
    assertThat(conta.heading("Perfil indisponível")).isVisible();
    assertThat(page.getByText("Este perfil não existe ou não está disponível.")).isVisible();
    assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Minha conta")))
        .isVisible();
    assertThat(page).hasTitle("Perfil indisponível · Duora");
  }

  private ContaPagina comConta(Participantes participantes) {
    return ContaPagina.de(
        Contas.comConta(
            participantes.novo(), contas.criar(EstadoConta.ATIVA, NASCIMENTO_DE_ADULTO), raiz()));
  }

  /** O perfil e a foto respondidos pelo Playwright: a tela não depende do armazém de imagens. */
  private static void responderPerfil(Page page, String perfil) {
    page.route(
        "**/api/perfis/" + OUTRA_CONTA,
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("application/json")
                    .setBody(perfil)));
    page.route(
        "**/api/fotos/**",
        rota ->
            rota.fulfill(
                new Route.FulfillOptions()
                    .setStatus(200)
                    .setContentType("image/png")
                    .setBodyBytes(FOTO)));
  }
}
