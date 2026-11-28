package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Orientacao;
import java.time.Instant;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DadosDeOrientacaoTest {

  private static final Instant AGORA = Instant.parse("2026-11-28T12:00:00Z");

  @Test
  void outraSemDescricaoRecusada() {
    recusado(() -> DadosDeOrientacao.de(Orientacao.OUTRA, null, false, AGORA));
  }

  @Test
  void gayComDescricaoRecusado() {
    recusado(() -> DadosDeOrientacao.de(Orientacao.GAY, "gay", false, AGORA));
  }

  @Test
  void outraCom41Recusada() {
    recusado(() -> DadosDeOrientacao.de(Orientacao.OUTRA, "a".repeat(41), false, AGORA));
  }

  @Test
  void orientacaoAusenteRecusada() {
    recusado(() -> DadosDeOrientacao.de(null, null, false, AGORA));
  }

  @Test
  void lesbicaExibeORotulo() {
    assertThat(DadosDeOrientacao.de(Orientacao.LESBICA, null, true, AGORA).paraExibir())
        .isEqualTo("Lésbica");
  }

  @Test
  void pansexualExibeORotulo() {
    assertThat(DadosDeOrientacao.de(Orientacao.PANSEXUAL, null, true, AGORA).paraExibir())
        .isEqualTo("Pansexual");
  }

  @Test
  void outraExibeADescricao() {
    assertThat(DadosDeOrientacao.de(Orientacao.OUTRA, " sapatão ", true, AGORA).paraExibir())
        .isEqualTo("sapatão");
  }

  @Test
  void toStringNaoMostraAOrientacao() {
    assertThat(DadosDeOrientacao.de(Orientacao.OUTRA, "sapatão", true, AGORA).toString())
        .doesNotContain("OUTRA", "sapatão");
  }

  private static void recusado(ThrowingCallable chamada) {
    assertThatThrownBy(chamada)
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.IDENTIDADE_INVALIDA));
  }
}
