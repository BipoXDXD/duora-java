package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.web.LimitesDeTaxa.Regra;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LimiteDeTaxaTest {

  private static final Duration QUINZE_MINUTOS = Duration.parse("PT15M");
  private static final Regra TRES_EM_QUINZE_MINUTOS = new Regra(3, QUINZE_MINUTOS);

  private final RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-11-22T12:00:00Z"));

  private LimiteDeTaxa limite(Map<String, Regra> regras, int multiplicador) {
    return new LimiteDeTaxa(new LimitesDeTaxa(regras, multiplicador), relogio);
  }

  private LimiteDeTaxa limiteDeEmail() {
    return limite(Map.of("entrar-email", TRES_EM_QUINZE_MINUTOS), 1);
  }

  private static void consumirVezes(LimiteDeTaxa limite, String regra, String chave, int vezes) {
    for (int i = 0; i < vezes; i++) {
      limite.consumir(regra, chave);
    }
  }

  @Test
  void quartoPedidoExcede() {
    LimiteDeTaxa limite = limiteDeEmail();
    consumirVezes(limite, "entrar-email", "chave-a", 3);

    assertThatThrownBy(() -> limite.consumir("entrar-email", "chave-a"))
        .isInstanceOfSatisfying(
            LimiteExcedido.class,
            erro -> {
              assertThat(erro.segundosParaTentarDeNovo()).isEqualTo(900);
              assertThat(erro.codigo()).isEqualTo(CodigoWeb.LIMITE_DE_TAXA);
              assertThat(erro.getMessage())
                  .isEqualTo("Muitas tentativas. Tente de novo em alguns minutos.");
              assertThat(erro.tentarDeNovoEm()).contains(Duration.ofSeconds(900));
            });
  }

  @Test
  void recargaDepoisDoPeriodo() {
    LimiteDeTaxa limite = limiteDeEmail();
    consumirVezes(limite, "entrar-email", "chave-a", 3);

    relogio.avancar(QUINZE_MINUTOS);

    assertThatCode(() -> consumirVezes(limite, "entrar-email", "chave-a", 3))
        .doesNotThrowAnyException();
  }

  @Test
  void chavesERegrasIsoladas() {
    LimiteDeTaxa limite =
        limite(
            Map.of("entrar-email", TRES_EM_QUINZE_MINUTOS, "entrar-ip", TRES_EM_QUINZE_MINUTOS), 1);
    consumirVezes(limite, "entrar-email", "chave-a", 3);

    assertThatCode(
            () -> {
              consumirVezes(limite, "entrar-email", "chave-b", 3);
              consumirVezes(limite, "entrar-ip", "chave-a", 3);
            })
        .doesNotThrowAnyException();
  }

  @Test
  void multiplicadorAumentaCapacidade() {
    LimiteDeTaxa limite = limite(Map.of("entrar-email", TRES_EM_QUINZE_MINUTOS), 2);
    consumirVezes(limite, "entrar-email", "chave-a", 6);

    assertThatThrownBy(() -> limite.consumir("entrar-email", "chave-a"))
        .isInstanceOf(LimiteExcedido.class);
  }

  @Test
  void regraDesconhecidaFalha() {
    LimiteDeTaxa limite = limiteDeEmail();

    assertThatThrownBy(() -> limite.consumir("inexistente", "chave-a"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("inexistente");
  }
}
