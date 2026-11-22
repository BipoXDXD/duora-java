package bipotheking.net.duora.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Peça, lugar e zona se comparam por valor: instâncias iguais, mas não idênticas, valem igual. */
class IgualdadePorValorTest {

  @Test
  void pecaAceitaLugarIgualMasNaoIdentico() {
    assertThat(Terraco.MANTA.aceita(new Lugar("L1", new Zona("lateral")))).isTrue();
  }

  @Test
  void ocupanteAchaLugarIgualMasNaoIdentico() {
    Composicao composicao = Composicao.vazia().com(Terraco.MANTA, Terraco.L1);

    assertThat(composicao.ocupante(new Lugar("L1", new Zona("lateral")))).contains(Terraco.MANTA);
  }

  @Test
  void lugaresDaZonaIgualMasNaoIdentica() {
    assertThat(Terraco.CENARIO.da(new Zona("centro"))).containsExactly(Terraco.C1, Terraco.C2);
  }

  @Test
  void lugaresPermitidosExcluiOLugarAtualPorValor() {
    Estado estado =
        new Estado(
            new Fase.Montando(),
            0,
            Composicao.vazia().com(Terraco.TELESCOPIO, new Lugar("C1", new Zona("centro"))),
            Set.of(),
            Optional.empty());

    assertThat(Regras.lugaresPermitidos(Terraco.CENARIO, estado, Terraco.TELESCOPIO))
        .containsExactly(Terraco.C2);
  }
}
