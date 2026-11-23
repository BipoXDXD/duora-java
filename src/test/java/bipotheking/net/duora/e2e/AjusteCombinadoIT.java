package bipotheking.net.duora.e2e;

import static bipotheking.net.duora.e2e.Cenarios.comporTerraco;
import static bipotheking.net.duora.e2e.Cenarios.entrarEmPar;
import static bipotheking.net.duora.e2e.Cenarios.raiz;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * O ajuste combinado da noite revelada: o pedido de Ana só volta à montagem depois do aceite de
 * Bruno, a recusa mantém a noite e a saída de Ana se despede de Bruno sem dizer quem saiu.
 */
// Aplicação nova por classe: as salas de um IT não somam com as de outro no limite de 20 salas
// ativas (LimitesSala), que é de produção e não muda nos testes.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
class AjusteCombinadoIT {

  @Value("${local.server.port}")
  private int porta;

  @Test
  void ajusteSoVoltaAMontagemDepoisDoAceite(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    revelar(ana, bruno);

    ana.pedirAjuste();

    assertThat(ana.page().locator("#ajuste-pedido")).hasText("Esperando Bruno aceitar.");
    assertThat(ana.aviso()).hasText("Pedido enviado. Esperando Bruno aceitar.");
    assertThat(bruno.aviso()).hasText("Ana quer ajustar a composição.");
    assertThat(ana.app()).hasAttribute("data-momento", "noite");

    bruno.aceitarAjuste();

    assertThat(bruno.app()).hasAttribute("data-momento", "crepusculo");
    assertThat(ana.app()).hasAttribute("data-momento", "crepusculo");
    assertThat(ana.aviso()).hasText("Bruno aceitou. De volta à montagem.");
  }

  @Test
  void recusaMantemANoite(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    revelar(ana, bruno);
    ana.pedirAjuste();

    bruno.recusarAjuste();

    assertThat(bruno.aviso()).hasText("Tudo bem, a noite fica como está.");
    assertThat(ana.aviso()).hasText("Bruno prefere manter esta noite.");
    assertThat(ana.botao("Pedir para ajustar")).isVisible();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(bruno.app()).hasAttribute("data-momento", "noite");
  }

  @Test
  void quemFicaVeOEncontroTerminouSemCulpa(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    revelar(ana, bruno);

    ana.sairDoTerraco();

    assertThat(titulo(ana, "Você saiu do terraço")).isVisible();
    assertThat(titulo(bruno, "O encontro terminou")).isVisible();
    assertThat(bruno.page().locator("#encerrado-texto"))
        .hasText("Que bom montar o terraço com Ana.");
    assertThat(bruno.page().locator("#encerrado")).not().containsText("saiu");
  }

  @Test
  void pedidoDeAjusteSemViolacoes(Participantes participantes) {
    TerracoPagina ana = participantes.novo();
    TerracoPagina bruno = participantes.novo();
    revelar(ana, bruno);

    ana.pedirAjuste();
    assertThat(bruno.botao("Aceitar o ajuste")).isVisible();
    assertThat(ana.botao("Cancelar pedido")).isVisible();

    Axe.assertSemViolacoes(ana.page(), "pedido de ajuste enviado");
    Axe.assertSemViolacoes(bruno.page(), "pedido de ajuste recebido");
  }

  private void revelar(TerracoPagina ana, TerracoPagina bruno) {
    entrarEmPar(ana, bruno, raiz(porta));
    comporTerraco(ana, bruno);
    ana.confirmar();
    bruno.botaoPronto().click();
    assertThat(ana.app()).hasAttribute("data-momento", "noite");
    assertThat(bruno.app()).hasAttribute("data-momento", "noite");
  }

  private static Locator titulo(TerracoPagina pagina, String nome) {
    return pagina
        .page()
        .getByRole(AriaRole.HEADING, new Page.GetByRoleOptions().setName(nome).setExact(true));
  }
}
