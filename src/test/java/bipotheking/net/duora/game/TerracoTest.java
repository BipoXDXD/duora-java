package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;
import static bipotheking.net.duora.game.Terraco.BANCO;
import static bipotheking.net.duora.game.Terraco.C1;
import static bipotheking.net.duora.game.Terraco.C2;
import static bipotheking.net.duora.game.Terraco.CENARIO;
import static bipotheking.net.duora.game.Terraco.L1;
import static bipotheking.net.duora.game.Terraco.LANTERNA;
import static bipotheking.net.duora.game.Terraco.MANTA;
import static bipotheking.net.duora.game.Terraco.R1;
import static bipotheking.net.duora.game.Terraco.TELESCOPIO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** O terraço como o primeiro cenário: dados e desfecho iguais aos de hoje. */
class TerracoTest {

  @Test
  void pecasNaOrdemDoContrato() {
    assertThat(CENARIO.pecas())
        .extracting(Peca::id)
        .containsExactly("telescopio", "manta", "radio", "banco", "lanterna", "garrafa");
  }

  @Test
  void lugaresNaOrdemDeHoje() {
    assertThat(CENARIO.lugares())
        .extracting(Lugar::id)
        .containsExactly("C1", "C2", "L1", "L2", "R1", "R2");
  }

  @Test
  void parametrosDeHoje() {
    assertThat(CENARIO.id()).isEqualTo("terraco");
    assertThat(CENARIO.nome()).isEqualTo("Terraço das Estrelas Cadentes");
    assertThat(CENARIO.limiteAntesDoParceiro()).isEqualTo(1);
    assertThat(CENARIO.duracaoSinal()).isEqualTo(Duration.ofSeconds(20));
    assertThat(CENARIO.pecasPorPessoaCompleta()).isEqualTo(2);
  }

  @Test
  void resultadoMantemAOrdemDasChaves() {
    Estado estado = aceito(Regras.parceiroEntrou(CENARIO, Estado.inicial(), Instant.EPOCH));
    estado = aceito(Regras.tocarAbertura(CENARIO, estado, A, Instant.EPOCH));
    estado = aceito(Regras.tocarAbertura(CENARIO, estado, B, Instant.EPOCH));
    estado = colocar(colocar(estado, TELESCOPIO, C1), BANCO, C2);
    estado = colocar(colocar(estado, MANTA, L1), LANTERNA, R1);
    estado = aceito(Regras.confirmar(CENARIO, estado, A, true, estado.versao()));
    estado = aceito(Regras.confirmar(CENARIO, estado, B, true, estado.versao()));

    assertThat(Regras.resultado(CENARIO, estado).orElseThrow().valores())
        .containsExactly(
            entry("secundariaA", "manta"),
            entry("secundariaB", "lanterna"),
            entry("ladoTelescopio", "esquerdo"));
  }

  @Test
  void ladoDoTelescopioComparaLugarPorValor() {
    Composicao composicao =
        Composicao.vazia()
            .com(TELESCOPIO, new Lugar("C1", new Zona("centro")))
            .com(BANCO, C2)
            .com(MANTA, L1)
            .com(LANTERNA, R1);

    assertThat(CENARIO.desfecho().de(composicao, CENARIO).valores())
        .containsEntry("ladoTelescopio", "esquerdo");
  }

  private static Estado colocar(Estado estado, Peca peca, Lugar lugar) {
    return aceito(
        Regras.colocar(CENARIO, estado, peca.dono(), peca, Optional.of(lugar), estado.versao()));
  }

  private static Estado aceito(Decisao decisao) {
    assertThat(decisao).isInstanceOf(Decisao.Aceita.class);
    return ((Decisao.Aceita) decisao).estado();
  }
}
