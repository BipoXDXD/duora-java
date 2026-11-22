package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;
import static bipotheking.net.duora.game.CenarioMinimo.CENARIO;
import static bipotheking.net.duora.game.CenarioMinimo.FOLHA;
import static bipotheking.net.duora.game.CenarioMinimo.P1;
import static bipotheking.net.duora.game.CenarioMinimo.P2;
import static bipotheking.net.duora.game.CenarioMinimo.PEDRA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** O cenário mínimo joga de ponta a ponta com as mesmas regras do terraço. */
class CenarioMinimoTest {

  private final Estado montando =
      aceito(Regras.parceiroEntrou(CENARIO, Estado.inicial(), Instant.EPOCH));

  @Test
  void faltamDuasNoComeco() {
    assertThat(CENARIO.faltam(Composicao.vazia())).isEqualTo(2);
  }

  @Test
  void colocarPrimeiraPecaAceita() {
    Estado estado = colocar(montando, PEDRA, P1);

    assertThat(estado.composicao().lugarDe(PEDRA)).contains(P1);
    assertThat(estado.versao()).isEqualTo(1);
  }

  @Test
  void confirmarAntesDeCompletarERecusado() {
    Estado estado = colocar(montando, PEDRA, P1);

    assertThat(Regras.confirmar(CENARIO, estado, A, true, estado.versao()))
        .isEqualTo(new Decisao.Recusada(Recusa.INCOMPLETA));
  }

  @Test
  void composicaoCompletaRevelaODesfechoDoCenario() {
    Estado estado = colocar(colocar(montando, PEDRA, P1), FOLHA, P2);
    estado = aceito(Regras.confirmar(CENARIO, estado, A, true, estado.versao()));
    estado = aceito(Regras.confirmar(CENARIO, estado, B, true, estado.versao()));

    assertThat(Regras.resultado(CENARIO, estado).orElseThrow().valores())
        .containsExactly(entry("juntos", "sim"));
  }

  @Test
  void sinalComDestinoNoCenarioMinimo() {
    Estado estado = colocar(montando, FOLHA, P1);

    assertThat(Regras.destinosSugeriveis(CENARIO, estado, FOLHA)).containsExactly(P2);
    assertThat(Regras.sinalizar(CENARIO, estado, A, FOLHA, P2, Instant.EPOCH))
        .isInstanceOf(Decisao.Aceita.class);
  }

  @Test
  void ajusteCombinadoNoCenarioMinimo() {
    Estado estado = colocar(colocar(montando, PEDRA, P1), FOLHA, P2);
    estado = aceito(Regras.confirmar(CENARIO, estado, A, true, estado.versao()));
    estado = aceito(Regras.confirmar(CENARIO, estado, B, true, estado.versao()));
    Estado pedido = aceito(Regras.pedirAjuste(CENARIO, estado, B));

    Estado ajustado = aceito(Regras.pedirAjuste(CENARIO, pedido, A));

    assertThat(pedido.fase()).isEqualTo(new Fase.Revelado(Optional.of(B)));
    assertThat(ajustado.fase()).isInstanceOf(Fase.Montando.class);
    assertThat(ajustado.versao()).isEqualTo(estado.versao() + 1);
  }

  @Test
  void idDeOutroCenarioNaoEPecaDesteCenario() {
    assertThat(CENARIO.peca("manta")).isEmpty();
    assertThat(CENARIO.lugar("C1")).isEmpty();
    assertThat(Terraco.CENARIO.peca("pedra")).isEmpty();
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
