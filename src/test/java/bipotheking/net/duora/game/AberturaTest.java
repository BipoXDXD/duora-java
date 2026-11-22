package bipotheking.net.duora.game;

import static bipotheking.net.duora.game.Assento.A;
import static bipotheking.net.duora.game.Assento.B;
import static bipotheking.net.duora.game.CenarioMinimo.COM_ABERTURA;
import static bipotheking.net.duora.game.CenarioMinimo.FOLHA;
import static bipotheking.net.duora.game.CenarioMinimo.P1;
import static bipotheking.net.duora.game.CenarioMinimo.P2;
import static bipotheking.net.duora.game.CenarioMinimo.PEDRA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** A abertura de acender junto: T0 é a entrada do par, janela de 5 s e máximo de 90 s. */
class AberturaTest {

  private static final Instant T0 = Instant.parse("2026-11-22T21:00:00Z");

  @Test
  void entradaDoParAbreAAbertura() {
    assertThat(emAbertura().fase())
        .isEqualTo(
            new Fase.Montando(Optional.of(new Abertura(T0, Map.of(), Optional.empty(), false))));
  }

  @Test
  void osDoisNaJanelaAcendem() {
    Abertura abertura = abertura(tocar(tocar(emAbertura(), A, 0), B, 4_999));

    assertThat(abertura.acesa()).isTrue();
    assertThat(abertura.ativa()).isFalse();
    assertThat(abertura.concluidaEm()).contains(em(4_999));
  }

  @Test
  void noLimiteDaJanelaAcende() {
    Abertura abertura = abertura(tocar(tocar(emAbertura(), A, 0), B, 5_000));

    assertThat(abertura.acesa()).isTrue();
    assertThat(abertura.concluidaEm()).contains(em(5_000));
  }

  @Test
  void foraDaJanelaNaoAcende() {
    Abertura abertura = abertura(tocar(tocar(emAbertura(), A, 0), B, 5_001));

    assertThat(abertura.ativa()).isTrue();
    assertThat(abertura.acesa()).isFalse();
    assertThat(abertura.toques()).containsOnly(entry(A, em(0)), entry(B, em(5_001)));
  }

  @Test
  void novoToqueDoPrimeiroDentroDaJanelaAcende() {
    Abertura abertura = abertura(tocar(tocar(tocar(emAbertura(), A, 0), B, 5_001), A, 6_000));

    assertThat(abertura.acesa()).isTrue();
    assertThat(abertura.concluidaEm()).contains(em(6_000));
  }

  @Test
  void doisToquesDoMesmoNaoAcendem() {
    Abertura abertura = abertura(tocar(tocar(emAbertura(), A, 0), A, 1_000));

    assertThat(abertura.ativa()).isTrue();
    assertThat(abertura.toques()).containsOnly(entry(A, em(1_000)));
  }

  @Test
  void aberturaTerminaSozinhaEm90s() {
    Estado inicio = emAbertura();

    Estado quase = Regras.avancarAbertura(COM_ABERTURA, inicio, em(89_999));
    Estado noMaximo = Regras.avancarAbertura(COM_ABERTURA, inicio, em(90_000));

    assertThat(quase).isSameAs(inicio);
    assertThat(abertura(noMaximo).ativa()).isFalse();
    assertThat(abertura(noMaximo).acesa()).isFalse();
  }

  @Test
  void aberturaSemToquesConcluiNoInstanteDoMaximo() {
    Estado avancado = Regras.avancarAbertura(COM_ABERTURA, emAbertura(), em(120_000));

    assertThat(abertura(avancado).concluidaEm()).contains(em(90_000));
  }

  @Test
  void avancarSemAberturaAtivaDevolveOMesmoEstado() {
    Estado acesa = acesa();
    Estado semAbertura = montandoSemAbertura();
    Estado aguardando = Estado.inicial();

    assertThat(Regras.avancarAbertura(COM_ABERTURA, acesa, em(200_000))).isSameAs(acesa);
    assertThat(Regras.avancarAbertura(COM_ABERTURA, semAbertura, em(200_000)))
        .isSameAs(semAbertura);
    assertThat(Regras.avancarAbertura(COM_ABERTURA, aguardando, em(200_000))).isSameAs(aguardando);
  }

  @Test
  void toqueDepoisDoMaximoNaoAcende() {
    Estado tocado = tocar(tocar(emAbertura(), A, 88_000), B, 91_000);

    assertThat(abertura(tocado).acesa()).isFalse();
    assertThat(abertura(tocado).concluidaEm()).contains(em(90_000));
  }

  @Test
  void toqueDepoisDeConcluidaNaoMudaNada() {
    Estado acesa = acesa();

    assertThat(Regras.tocarAbertura(COM_ABERTURA, acesa, A, em(7_000)))
        .isEqualTo(new Decisao.Aceita(acesa));
  }

  @Test
  void tocarForaDaMontagemERecusado() {
    Decisao.Recusada faseInvalida = new Decisao.Recusada(Recusa.FASE_INVALIDA);

    assertThat(Regras.tocarAbertura(COM_ABERTURA, Estado.inicial(), A, T0)).isEqualTo(faseInvalida);
    assertThat(Regras.tocarAbertura(COM_ABERTURA, revelado(), A, T0)).isEqualTo(faseInvalida);
    assertThat(Regras.tocarAbertura(CenarioMinimo.CENARIO, montandoSemAbertura(), A, T0))
        .isEqualTo(faseInvalida);
  }

  @Test
  void tocarNaoMudaVersao() {
    Estado tocado = tocar(emAbertura(), A, 0);
    Estado aceso = tocar(tocado, B, 1_000);

    assertThat(tocado.versao()).isZero();
    assertThat(aceso.versao()).isZero();
    assertThat(aceso.composicao()).isEqualTo(Composicao.vazia());
  }

  @Test
  void pecaDuranteAberturaRecusada() {
    Estado estado = ativaComFolhaEmP1();

    assertThat(Regras.colocar(COM_ABERTURA, estado, A, PEDRA, Optional.of(P2), 1))
        .isEqualTo(new Decisao.Recusada(Recusa.ABERTURA_EM_ANDAMENTO));
    assertThat(Regras.colocar(COM_ABERTURA, estado, B, FOLHA, Optional.empty(), 1))
        .isEqualTo(new Decisao.Recusada(Recusa.ABERTURA_EM_ANDAMENTO));
  }

  @Test
  void sinalDuranteAberturaRecusado() {
    assertThat(Regras.sinalizar(COM_ABERTURA, ativaComFolhaEmP1(), A, FOLHA, P2, em(1_000)))
        .isEqualTo(new Decisao.Recusada(Recusa.ABERTURA_EM_ANDAMENTO));
  }

  @Test
  void prontoDuranteAberturaRecusado() {
    assertThat(Regras.confirmar(COM_ABERTURA, ativaCompleta(), A, true, 2))
        .isEqualTo(new Decisao.Recusada(Recusa.ABERTURA_EM_ANDAMENTO));
  }

  @Test
  void lugaresPermitidosEDestinosVaziosDuranteAbertura() {
    Estado estado = ativaComFolhaEmP1();

    assertThat(Regras.lugaresPermitidos(COM_ABERTURA, estado, PEDRA)).isEmpty();
    assertThat(Regras.destinosSugeriveis(COM_ABERTURA, estado, FOLHA)).isEmpty();
  }

  @Test
  void recusasEIdempotenciasAnterioresValemDuranteAbertura() {
    Estado estado = ativaComFolhaEmP1();

    assertThat(Regras.colocar(COM_ABERTURA, estado, A, FOLHA, Optional.of(P2), 1))
        .isEqualTo(new Decisao.Recusada(Recusa.PECA_ALHEIA));
    assertThat(Regras.colocar(COM_ABERTURA, estado, B, FOLHA, Optional.of(P1), 1))
        .isEqualTo(new Decisao.Aceita(estado));
    assertThat(Regras.sinalizar(COM_ABERTURA, estado, B, FOLHA, P2, em(1_000)))
        .isEqualTo(new Decisao.Recusada(Recusa.PECA_ALHEIA));
    assertThat(Regras.confirmar(COM_ABERTURA, estado, A, false, 1))
        .isEqualTo(new Decisao.Aceita(estado));
  }

  @Test
  void naoPodeConfirmarDuranteAbertura() {
    assertThat(Regras.podeConfirmar(COM_ABERTURA, ativaCompleta())).isFalse();
  }

  @Test
  void podeConfirmarComAberturaConcluidaECompleta() {
    Estado completa = colocar(colocar(acesa(), PEDRA, P1), FOLHA, P2);

    assertThat(Regras.podeConfirmar(COM_ABERTURA, completa)).isTrue();
  }

  @Test
  void colocarPreservaAAberturaConcluida() {
    Estado acesa = acesa();

    Estado colocado = colocar(acesa, PEDRA, P1);

    assertThat(colocado.fase()).isEqualTo(acesa.fase());
    assertThat(abertura(colocado).acesa()).isTrue();
  }

  @Test
  void colocarNoLugarAtualComAberturaConcluidaNaoMudaNada() {
    Estado colocado = colocar(acesa(), PEDRA, P1);

    assertThat(Regras.colocar(COM_ABERTURA, colocado, A, PEDRA, Optional.of(P1), 0))
        .isEqualTo(new Decisao.Aceita(colocado));
  }

  @Test
  void ajusteVoltaAMontagemSemAbertura() {
    Estado pedido = aceito(Regras.pedirAjuste(COM_ABERTURA, revelado(), A));

    Estado ajustado = aceito(Regras.pedirAjuste(COM_ABERTURA, pedido, B));

    assertThat(ajustado.fase()).isEqualTo(new Fase.Montando(Optional.empty()));
  }

  @Test
  void maximoZeroConcluiNaPrimeiraAvancada() {
    Cenario semEspera = COM_ABERTURA.comMaximoDeAbertura(Duration.ZERO);
    Estado entrada = aceito(Regras.parceiroEntrou(semEspera, Estado.inicial(), T0));

    Estado avancado = Regras.avancarAbertura(semEspera, entrada, T0);

    assertThat(abertura(entrada).ativa()).isTrue();
    assertThat(abertura(avancado).ativa()).isFalse();
    assertThat(abertura(avancado).acesa()).isFalse();
  }

  @Test
  void cenarioSemAberturaComecaMontando() {
    assertThat(montandoSemAbertura().fase()).isEqualTo(new Fase.Montando(Optional.empty()));
  }

  @Test
  void nomeDaFaseNaRedeNaoMuda() {
    assertThat(emAbertura().fase().nome()).isEqualTo("MONTANDO");
  }

  private static Estado emAbertura() {
    return aceito(Regras.parceiroEntrou(COM_ABERTURA, Estado.inicial(), T0));
  }

  private static Estado montandoSemAbertura() {
    return aceito(Regras.parceiroEntrou(CenarioMinimo.CENARIO, Estado.inicial(), T0));
  }

  /** A em 0 s e B em 1 s: concluída acesa em T0 + 1 s. */
  private static Estado acesa() {
    return tocar(tocar(emAbertura(), A, 0), B, 1_000);
  }

  private static Estado revelado() {
    Estado completa = colocar(colocar(acesa(), PEDRA, P1), FOLHA, P2);
    Estado umPronto = aceito(Regras.confirmar(COM_ABERTURA, completa, A, true, 2));
    return aceito(Regras.confirmar(COM_ABERTURA, umPronto, B, true, 2));
  }

  /** Abertura ativa com a folha já em P1 (montado à mão, para as recusas não dependerem dela). */
  private static Estado ativaComFolhaEmP1() {
    return new Estado(
        emAbertura().fase(), 1, Composicao.vazia().com(FOLHA, P1), Set.of(), Optional.empty());
  }

  private static Estado ativaCompleta() {
    return new Estado(
        emAbertura().fase(),
        2,
        Composicao.vazia().com(PEDRA, P1).com(FOLHA, P2),
        Set.of(),
        Optional.empty());
  }

  private static Estado tocar(Estado estado, Assento quem, long millis) {
    return aceito(Regras.tocarAbertura(COM_ABERTURA, estado, quem, em(millis)));
  }

  private static Estado colocar(Estado estado, Peca peca, Lugar lugar) {
    return aceito(
        Regras.colocar(
            COM_ABERTURA, estado, peca.dono(), peca, Optional.of(lugar), estado.versao()));
  }

  private static Abertura abertura(Estado estado) {
    assertThat(estado.fase()).isInstanceOf(Fase.Montando.class);
    return ((Fase.Montando) estado.fase()).abertura().orElseThrow();
  }

  private static Instant em(long millis) {
    return T0.plusMillis(millis);
  }

  private static Estado aceito(Decisao decisao) {
    assertThat(decisao).isInstanceOf(Decisao.Aceita.class);
    return ((Decisao.Aceita) decisao).estado();
  }
}
