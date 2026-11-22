package bipotheking.net.duora.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Peça, lugar e zona se comparam por valor: instâncias iguais, mas não idênticas, valem igual. */
class IgualdadePorValorTest {

  @Test
  void pecaAceitaLugarIgualMasNaoIdentico() {
    assertThat(Peca.MANTA.aceita(new Lugar("L1", new Zona("lateral")))).isTrue();
  }

  @Test
  void ocupanteAchaLugarIgualMasNaoIdentico() {
    Composicao composicao = Composicao.vazia().com(Peca.MANTA, Lugar.L1);

    assertThat(composicao.ocupante(new Lugar("L1", new Zona("lateral")))).contains(Peca.MANTA);
  }

  @Test
  void lugaresDaZonaIgualMasNaoIdentica() {
    assertThat(Lugar.da(new Zona("centro"))).containsExactly(Lugar.C1, Lugar.C2);
  }

  @Test
  void lugaresPermitidosExcluiOLugarAtualPorValor() {
    Estado estado =
        new Estado(
            Fase.MONTANDO,
            0,
            Composicao.vazia().com(Peca.TELESCOPIO, new Lugar("C1", new Zona("centro"))),
            Set.of(),
            Optional.empty());

    assertThat(Regras.lugaresPermitidos(estado, Peca.TELESCOPIO)).containsExactly(Lugar.C2);
  }
}
