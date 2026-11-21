package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.game.Assento;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Registro das salas: varredura, lápides e teto de memória. */
class SalasTest {

  private static final Duration LAPIDE = LimitesSala.PADRAO.lapide();

  private final RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-11-20T21:00:00Z"));

  @Test
  void varrerMantemALapideAte5Minutos() {
    Salas salas = salas(20, 40);
    Salas.Entrada ana = salas.criar(Apelido.de("Ana"));
    sair(salas, ana);
    relogio.avancar(LAPIDE.minusSeconds(1));

    salas.varrer();

    assertThat(salas.total()).isEqualTo(1);
    assertCodigo(() -> consultar(salas, ana), Codigo.SALA_ENCERRADA);
  }

  @Test
  void varrerEsqueceLapideVencidaComAssentosEConvite() {
    Salas salas = salas(20, 40);
    Salas.Entrada ana = salas.criar(Apelido.de("Ana"));
    String convite = ana.visao().convite();
    Salas.Entrada bruno = salas.entrar(convite, Apelido.de("Bruno"));
    sair(salas, ana);
    relogio.avancar(LAPIDE);

    salas.varrer();

    assertThat(salas.total()).isZero();
    assertCodigo(() -> salas.localizar(Optional.of(ana.token())), Codigo.SALA_INEXISTENTE);
    assertCodigo(() -> salas.localizar(Optional.of(bruno.token())), Codigo.SALA_INEXISTENTE);
  }

  @Test
  void varrerEsqueceConviteDeSalaNuncaUsada() {
    Salas salas = salas(20, 40);
    Salas.Entrada ana = salas.criar(Apelido.de("Ana"));
    relogio.avancar(LimitesSala.PADRAO.inatividade().plus(LAPIDE));

    salas.varrer();

    assertThat(salas.total()).isZero();
    assertCodigo(
        () -> salas.entrar(ana.visao().convite(), Apelido.de("Bruno")), Codigo.CONVITE_INVALIDO);
  }

  @Test
  void varrerExpiraSalaInativaSemDescartarAntesDaLapideVencer() {
    Salas salas = salas(20, 40);
    Salas.Entrada ana = salas.criar(Apelido.de("Ana"));
    relogio.avancar(LimitesSala.PADRAO.inatividade());

    salas.varrer();

    assertThat(salas.total()).isEqualTo(1);
    assertCodigo(() -> consultar(salas, ana), Codigo.SALA_ENCERRADA);
  }

  @Test
  void conviteSaiDoIndiceQuandoBEntra() {
    Salas salas = salas(20, 40);
    Salas.Entrada ana = salas.criar(Apelido.de("Ana"));
    String convite = ana.visao().convite();
    salas.entrar(convite, Apelido.de("Bruno"));

    assertCodigo(() -> salas.entrar(convite, Apelido.de("Carla")), Codigo.CONVITE_INVALIDO);
  }

  @Test
  void noTetoALapideMaisAntigaEhDescartadaParaAbrirVaga() {
    Salas salas = salas(2, 3);
    Salas.Entrada primeira = salas.criar(Apelido.de("Ana"));
    sair(salas, primeira);
    relogio.avancar(Duration.ofSeconds(1));
    Salas.Entrada segunda = salas.criar(Apelido.de("Bia"));
    sair(salas, segunda);
    salas.criar(Apelido.de("Cris"));

    salas.criar(Apelido.de("Duda"));

    assertThat(salas.total()).isEqualTo(3);
    assertCodigo(() -> salas.localizar(Optional.of(primeira.token())), Codigo.SALA_INEXISTENTE);
    assertCodigo(() -> consultar(salas, segunda), Codigo.SALA_ENCERRADA);
  }

  @Test
  void noTetoSemLapidesACriacaoEhRecusada() {
    Salas salas = salas(5, 3);
    for (int i = 0; i < 3; i++) {
      salas.criar(Apelido.de("Ana"));
    }

    assertCodigo(() -> salas.criar(Apelido.de("Bia")), Codigo.LIMITE_SALAS);
    assertThat(salas.total()).isEqualTo(3);
  }

  @Test
  void limiteDeAtivasValeMesmoAbaixoDoTeto() {
    Salas salas = salas(2, 40);
    salas.criar(Apelido.de("Ana"));
    salas.criar(Apelido.de("Bia"));

    assertCodigo(() -> salas.criar(Apelido.de("Cris")), Codigo.LIMITE_SALAS);
  }

  private Salas salas(int maximoAtivas, int maximoTotal) {
    LimitesSala padrao = LimitesSala.PADRAO;
    return new Salas(
        relogio,
        new LimitesSala(
            maximoAtivas, maximoTotal, padrao.inatividade(), padrao.lapide(), padrao.presenca()));
  }

  private static void sair(Salas salas, Salas.Entrada entrada) {
    salas.localizar(Optional.of(entrada.token())).sala().sair();
  }

  private static Visao consultar(Salas salas, Salas.Entrada entrada) {
    return salas.localizar(Optional.of(entrada.token())).sala().consultar(Assento.A);
  }

  private static void assertCodigo(Executable acao, Codigo esperado) {
    assertThatThrownBy(acao::execute)
        .isInstanceOfSatisfying(
            ErroApi.class, erro -> assertThat(erro.codigo()).isEqualTo(esperado));
  }
}
