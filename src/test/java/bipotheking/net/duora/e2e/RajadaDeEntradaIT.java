package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O limite de pedidos por e-mail visto na tela. Classe e contexto próprios: só aqui os limites
 * voltam ao valor da regra (o failsafe os folga para as outras jornadas).
 */
class RajadaDeEntradaIT extends ContaE2eBase {

  private static final int PEDIDOS_ACIMA_DO_LIMITE = 4;

  @DynamicPropertySource
  static void limitesDaRegra(DynamicPropertyRegistry registro) {
    registro.add("duora.limites.multiplicador", () -> 1);
  }

  @Test
  void rajadaMostraAviso(Participantes participantes) {
    TerracoPagina pagina = participantes.novo();
    TurnstileFalso.instalar(pagina.page());
    ContaPagina conta = ContaPagina.de(pagina).abrir(raiz() + "entrar");
    String email = emailNovo();

    for (int pedido = 0; pedido < PEDIDOS_ACIMA_DO_LIMITE; pedido++) {
      conta.pedirLink(email);
    }

    assertThat(conta.page().getByText("Muitas tentativas. Tente de novo em")).isVisible();
  }
}
