package bipotheking.net.duora.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.e2e.NavegadorExtension.Participantes;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O limite de pedidos por e-mail visto na tela. Classe e contexto próprios: só aqui os limites
 * voltam ao valor da regra (o failsafe os folga para as outras jornadas).
 */
class RajadaDeEntradaIT extends ContaE2eBase {

  /** A regra {@code entrar-email} aceita 3 pedidos por e-mail a cada 15 min. */
  private static final int PEDIDOS_DENTRO_DO_LIMITE = 3;

  private static final Pattern AVISO_DE_ESPERA =
      Pattern.compile("^Muitas tentativas\\. Tente de novo em \\d+ minutos?\\.$");

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

    for (int pedido = 0; pedido < PEDIDOS_DENTRO_DO_LIMITE; pedido++) {
      Response resposta = conta.pedirLink(email);
      assertThat(resposta.status()).isEqualTo(202);
      assertThat(status(conta))
          .hasText("Enviamos um link para o seu e-mail. Ele vale por 15 minutos.");
      assertThat(alerta(conta)).isEmpty();
    }

    Response recusa = conta.pedirLink(email);

    assertThat(recusa.status()).isEqualTo(429);
    assertThat(alerta(conta)).hasText(AVISO_DE_ESPERA);
  }

  private static Locator status(ContaPagina conta) {
    return conta.page().getByRole(AriaRole.STATUS);
  }

  private static Locator alerta(ContaPagina conta) {
    return conta.page().getByRole(AriaRole.ALERT);
  }
}
