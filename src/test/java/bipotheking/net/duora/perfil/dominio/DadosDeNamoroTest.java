package bipotheking.net.duora.perfil.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Genero;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

class DadosDeNamoroTest {

  @Test
  void outroSemDescricaoRecusado() {
    recusado(() -> DadosDeNamoro.de(Genero.OUTRO, null, List.of(Genero.MULHER)));
  }

  @Test
  void mulherComDescricaoRecusada() {
    recusado(() -> DadosDeNamoro.de(Genero.MULHER, "mulher", List.of(Genero.MULHER)));
  }

  @Test
  void interessesVaziosRecusados() {
    recusado(() -> DadosDeNamoro.de(Genero.MULHER, null, List.of()));
  }

  @Test
  void outroComDescricaoEInteressesAceito() {
    DadosDeNamoro dados =
        DadosDeNamoro.de(Genero.OUTRO, "agênero", List.of(Genero.OUTRO, Genero.MULHER));

    assertThat(dados.genero()).isEqualTo(Genero.OUTRO);
    assertThat(dados.descricao()).map(DescricaoCurta::valor).contains("agênero");
    assertThat(dados.interesses()).containsExactly(Genero.MULHER, Genero.OUTRO);
  }

  private static void recusado(ThrowingCallable chamada) {
    assertThatThrownBy(chamada)
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.GENERO_INVALIDO));
  }
}
