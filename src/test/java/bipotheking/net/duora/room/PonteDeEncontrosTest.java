package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.CatalogoDeCenarios;
import bipotheking.net.duora.game.Terraco;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.Perfis;
import bipotheking.net.duora.sala.EncontroAberto;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Sala pareada ligada às contas: cada teste usa um registro de salas novo, para a contagem não
 * herdar salas de outros testes.
 */
class PonteDeEncontrosTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1995, 3, 1);

  @Autowired private ContasDeTeste contas;
  @Autowired private Perfis perfis;
  @Autowired private CatalogoDeCenarios catalogo;

  private Salas salas;
  private PonteDeEncontros ponte;
  private ContaId ana;
  private ContaId bruno;

  @BeforeEach
  void montar() {
    usarLimites(LimitesSala.PADRAO);
    ana = contas.criar("Ana", NASCIMENTO).id();
    bruno = contas.criar("Bruno", NASCIMENTO).id();
  }

  @Test
  void abrirPareadoCriaSalaComOsDoisApelidos() {
    EncontroAberto aberto = abrir(ana, bruno);

    salas.reivindicar(aberto.encontroId(), bruno);
    Visao visaoDeAna = salas.reivindicar(aberto.encontroId(), ana).visao();

    assertThat(aberto).isEqualTo(new EncontroAberto(aberto.encontroId(), Terraco.ID, ana, bruno));
    assertThat(visaoDeAna.voce()).isEqualTo(new Visao.Voce(Assento.A, "Ana"));
    assertThat(visaoDeAna.parceiro()).isNotNull();
    assertThat(visaoDeAna.parceiro().nome()).isEqualTo("Bruno");
  }

  @Test
  void reivindicarEmiteTokenQueAbreASala() {
    String id = abrir(ana, bruno).encontroId();

    Salas.Entrada entrada = salas.reivindicar(id, ana);

    Salas.Assentado assentado = salas.localizar(Optional.of(entrada.token()));
    assertThat(assentado.sala().id()).isEqualTo(id);
    assertThat(assentado.assento()).isEqualTo(Assento.A);
    assertThat(entrada.visao().encontroId()).isEqualTo(id);
  }

  @Test
  void reivindicarDeNovoInvalidaOTokenAnterior() {
    String id = abrir(ana, bruno).encontroId();
    Salas.Entrada primeira = salas.reivindicar(id, bruno);

    Salas.Entrada segunda = salas.reivindicar(id, bruno);

    assertThatThrownBy(() -> salas.localizar(Optional.of(primeira.token())))
        .isInstanceOfSatisfying(
            ErroApi.class, erro -> assertThat(erro.codigo()).isEqualTo(Codigo.SALA_INEXISTENTE));
    assertThat(salas.localizar(Optional.of(segunda.token())).assento()).isEqualTo(Assento.B);
  }

  @Test
  void bSoEntraNoJogoAoReivindicar() {
    String id = abrir(ana, bruno).encontroId();
    Salas.Entrada deAna = salas.reivindicar(id, ana);
    assertThat(deAna.visao().parceiro()).isNull();
    assertThat(deAna.visao().fase()).isEqualTo("AGUARDANDO_PAR");

    salas.reivindicar(id, bruno);

    Visao depois = salas.localizar(Optional.of(deAna.token())).sala().consultar(Assento.A);
    assertThat(depois.parceiro()).isNotNull();
    assertThat(depois.fase()).isNotEqualTo("AGUARDANDO_PAR");
  }

  @Test
  void presenteSoDepoisDeReivindicar() {
    String id = abrir(ana, bruno).encontroId();
    assertThat(ponte.presente(id, ana)).isFalse();

    salas.reivindicar(id, ana);

    assertThat(ponte.presente(id, ana)).isTrue();
    assertThat(ponte.presente(id, bruno)).isFalse();
  }

  @Test
  void cancelarEncerraComMotivoCancelada() {
    String id = abrir(ana, bruno).encontroId();
    Salas.Entrada deAna = salas.reivindicar(id, ana);

    ponte.cancelar(id);

    Salas.Assentado assentado = salas.localizar(Optional.of(deAna.token()));
    assertThatThrownBy(() -> assentado.sala().consultar(Assento.A))
        .isInstanceOfSatisfying(
            ErroApi.class,
            erro -> {
              assertThat(erro.codigo()).isEqualTo(Codigo.SALA_ENCERRADA);
              assertThat(erro.extras()).containsEntry("motivo", "CANCELADA");
              assertThat(erro.getMessage()).isEqualTo("A outra pessoa não chegou a tempo.");
            });
    assertThat(ponte.encontroAtivoDe(ana)).isEmpty();
  }

  @Test
  void ativosNaoContamOTerracoAnonimo() {
    salas.criar(Apelido.de("Carla"), Terraco.CENARIO);

    abrir(ana, bruno);

    assertThat(ponte.ativos()).isEqualTo(1);
  }

  @Test
  void pareadaNaoConsomeVagaDoTerracoAnonimo() {
    LimitesSala padrao = LimitesSala.PADRAO;
    usarLimites(
        new LimitesSala(
            1, padrao.maximoTotal(), padrao.inatividade(), padrao.lapide(), padrao.presenca()));
    abrir(ana, bruno);

    Salas.Entrada anonima = salas.criar(Apelido.de("Carla"), Terraco.CENARIO);

    assertThat(anonima.visao().encontroId()).isNull();
  }

  @Test
  void terminosPrevistosEmOrdem() {
    ContaId carla = contas.criar("Carla", NASCIMENTO).id();
    ContaId davi = contas.criar("Davi", NASCIMENTO).id();
    abrir(ana, bruno);
    relogio.avancar(Duration.ofMinutes(5));
    abrir(carla, davi);

    assertThat(ponte.terminosPrevistos())
        .containsExactly(INICIO.plus(30, ChronoUnit.MINUTES), INICIO.plus(35, ChronoUnit.MINUTES));
  }

  @Test
  void privadoAindaNaoImplementado() {
    assertThatThrownBy(() -> ponte.abrirPrivado(ana, Terraco.ID))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("convite privado: E3");
    assertThatThrownBy(() -> ponte.entrarPrivado("convite", bruno))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("convite privado: E3");
    assertThatThrownBy(() -> ponte.criadorDoConvite("convite"))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessage("convite privado: E3");
  }

  private void usarLimites(LimitesSala limites) {
    salas = new Salas(relogio, limites);
    ponte = new PonteDeEncontros(salas, perfis, catalogo);
  }

  private EncontroAberto abrir(ContaId a, ContaId b) {
    return ponte.abrirPareado(new ParPareado(a, b, Set.of(Intencao.AMIZADE)), Terraco.ID);
  }
}
