package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;
import static bipotheking.net.duora.game.Terraco.BANCO;
import static bipotheking.net.duora.game.Terraco.C1;
import static bipotheking.net.duora.game.Terraco.C2;
import static bipotheking.net.duora.game.Terraco.L1;
import static bipotheking.net.duora.game.Terraco.MANTA;
import static bipotheking.net.duora.game.Terraco.TELESCOPIO;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Portão, sinal, completude e troca seguem o cenário, não constantes do terraço. */
class ParametrosDoCenarioTest {

  private static final Instant AGORA = Instant.parse("2026-11-22T21:00:00Z");

  @Test
  void limiteDoisDeixaSegundaPecaAntesDoParceiro() {
    Cenario cenario = variante(2, Duration.ofSeconds(20), 2);
    Estado estado = colocar(cenario, montando(cenario), TELESCOPIO, C1);

    assertThat(Regras.colocar(cenario, estado, A, MANTA, Optional.of(L1), estado.versao()))
        .isInstanceOf(Decisao.Aceita.class);
  }

  @Test
  void limiteUmRecusaSegundaPecaAntesDoParceiro() {
    Cenario cenario = variante(1, Duration.ofSeconds(20), 2);
    Estado estado = colocar(cenario, montando(cenario), TELESCOPIO, C1);

    assertThat(Regras.colocar(cenario, estado, A, MANTA, Optional.of(L1), estado.versao()))
        .isEqualTo(new Decisao.Recusada(Recusa.ESPERE_PRIMEIRA_PECA));
  }

  @Test
  void bloqueioDoPortaoSegueOLimite() {
    Cenario limiteDois = variante(2, Duration.ofSeconds(20), 2);
    Cenario limiteUm = variante(1, Duration.ofSeconds(20), 2);
    Estado estado = colocar(limiteUm, montando(limiteUm), TELESCOPIO, C1);

    assertThat(Regras.bloqueadaPeloPortao(limiteDois, estado, MANTA)).isFalse();
    assertThat(Regras.bloqueadaPeloPortao(limiteUm, estado, MANTA)).isTrue();
  }

  @Test
  void sinalDuraOQueOCenarioDiz() {
    Cenario cenario = variante(1, Duration.ofSeconds(5), 2);
    Estado estado = colocar(cenario, montando(cenario), TELESCOPIO, C1);

    Estado sinalizado = aceito(Regras.sinalizar(cenario, estado, B, TELESCOPIO, C2, AGORA));

    assertThat(sinalizado.sinal().orElseThrow().expiraEm()).isEqualTo(AGORA.plusSeconds(5));
  }

  @Test
  void faltamSegueOCenario() {
    Cenario cenario = variante(1, Duration.ofSeconds(20), 1);
    Composicao composicao = Composicao.vazia().com(TELESCOPIO, C1).com(BANCO, C2);

    assertThat(cenario.faltam(Composicao.vazia())).isEqualTo(2);
    assertThat(cenario.completa(composicao)).isTrue();
  }

  @Test
  void trocaDeSecundariaUsaAsPecasDoCenario() {
    Zona meio = new Zona("meio");
    Zona borda = new Zona("borda");
    Lugar m1 = new Lugar("M1", meio);
    Lugar m2 = new Lugar("M2", meio);
    Lugar b1 = new Lugar("B1", borda);
    Lugar b2 = new Lugar("B2", borda);
    Peca estrela = new Peca("estrela", A, meio, true);
    Peca lua = new Peca("lua", A, borda, false);
    Peca sol = new Peca("sol", A, borda, false);
    Peca nuvem = new Peca("nuvem", B, meio, true);
    Cenario troca =
        new Cenario(
            "troca",
            "Troca",
            List.of(m1, m2, b1, b2),
            List.of(estrela, lua, sol, nuvem),
            1,
            Duration.ofSeconds(20),
            1,
            (composicao, cenario) -> new Resultado(Map.of()));
    Estado estado = colocar(troca, montando(troca), nuvem, m2);
    estado = colocar(troca, colocar(troca, estado, estrela, m1), lua, b1);

    estado = colocar(troca, estado, sol, b2);

    assertThat(estado.composicao().lugarDe(lua)).isEmpty();
    assertThat(estado.composicao().lugarDe(sol)).contains(b2);
  }

  /** Cópia do terraço (mesmos lugares, peças e desfecho) com outros parâmetros. */
  private static Cenario variante(int limite, Duration sinal, int porPessoa) {
    Cenario terraco = Terraco.CENARIO;
    return new Cenario(
        "terraco-variante",
        terraco.nome(),
        terraco.lugares(),
        terraco.pecas(),
        limite,
        sinal,
        porPessoa,
        terraco.desfecho());
  }

  private static Estado montando(Cenario cenario) {
    return aceito(Regras.parceiroEntrou(cenario, Estado.inicial(), AGORA));
  }

  private static Estado colocar(Cenario cenario, Estado estado, Peca peca, Lugar lugar) {
    return aceito(
        Regras.colocar(cenario, estado, peca.dono(), peca, Optional.of(lugar), estado.versao()));
  }

  private static Estado aceito(Decisao decisao) {
    assertThat(decisao).isInstanceOf(Decisao.Aceita.class);
    return ((Decisao.Aceita) decisao).estado();
  }
}
