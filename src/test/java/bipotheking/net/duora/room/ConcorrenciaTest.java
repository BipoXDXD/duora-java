package bipotheking.net.duora.room;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.game.Assento;
import bipotheking.net.duora.game.Terraco;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pedidos simultâneos sobre a mesma sala, repetidos centenas de vezes. */
class ConcorrenciaTest {

  private static final int REPETICOES = 300;
  private static final int SEM_LIMITE_DE_SALAS = 100_000;

  private final RelogioDeTeste relogio = new RelogioDeTeste(Instant.parse("2026-11-20T21:00:00Z"));
  private final ExecutorService pool = Executors.newFixedThreadPool(32);

  @AfterEach
  void encerrarPool() {
    pool.shutdownNow();
  }

  @Test
  void duasPessoasNoMesmoLugarAoMesmoTempoSoUmaVence() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Mesa mesa = mesaMontando(salas);

      List<Desfecho> desfechos =
          simultaneamente(
              () -> mesa.a().colocar(Assento.A, Terraco.TELESCOPIO, Optional.of(Terraco.C1), 0),
              () -> mesa.b().colocar(Assento.B, Terraco.BANCO, Optional.of(Terraco.C1), 0));

      assertThat(desfechos).filteredOn(Desfecho::aceito).hasSize(1);
      assertThat(desfechos)
          .filteredOn(d -> !d.aceito())
          .extracting(Desfecho::erro)
          .containsExactly(Optional.of(Codigo.LUGAR_OCUPADO));
      Visao finalA = mesa.a().consultar(Assento.A);
      assertThat(finalA.versao()).isEqualTo(1);
      assertThat(finalA.pecas()).filteredOn(p -> "C1".equals(lugarDe(p))).hasSize(1);
    }
  }

  @Test
  void duasEntradasSimultaneasComOMesmoConviteSoUmaVence() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Salas.Entrada criacao = salas.criar(Apelido.de("Ana"), Terraco.CENARIO);
      String convite = criacao.visao().convite();

      List<Desfecho> desfechos =
          simultaneamente(
              () -> salas.entrar(convite, Apelido.de("Bruno")).visao(),
              () -> salas.entrar(convite, Apelido.de("Carla")).visao());

      assertThat(desfechos).filteredOn(Desfecho::aceito).hasSize(1);
      assertThat(desfechos)
          .filteredOn(d -> !d.aceito())
          .extracting(Desfecho::erro)
          .containsExactly(Optional.of(Codigo.CONVITE_INVALIDO));
      String vencedor =
          desfechos.stream()
              .filter(Desfecho::aceito)
              .findFirst()
              .orElseThrow()
              .visao()
              .voce()
              .nome();
      Salas.Assentado a = salas.localizar(Optional.of(criacao.token()));
      assertThat(a.sala().consultar(Assento.A).parceiro().nome()).isEqualTo(vencedor);
    }
  }

  @Test
  void confirmacoesSimultaneasRevelamUmaSoVez() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Mesa mesa = mesaCompleta(salas);

      List<Desfecho> desfechos =
          simultaneamente(
              () -> mesa.a().confirmar(Assento.A, true, 4),
              () -> mesa.b().confirmar(Assento.B, true, 4));

      assertThat(desfechos).allMatch(Desfecho::aceito);
      assertThat(desfechos).filteredOn(d -> "REVELADO".equals(d.visao().fase())).hasSize(1);
      Visao finalA = mesa.a().consultar(Assento.A);
      assertThat(finalA.fase()).isEqualTo("REVELADO");
      assertThat(finalA.versao()).isEqualTo(4);
      assertThat(finalA.prontos()).isEqualTo(new Visao.Prontos(true, true));
    }
  }

  @Test
  void envioDuplicadoNaoDuplicaAMudanca() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Mesa mesa = mesaMontando(salas);

      List<Desfecho> desfechos =
          simultaneamente(
              () -> mesa.a().colocar(Assento.A, Terraco.TELESCOPIO, Optional.of(Terraco.C2), 0),
              () -> mesa.a().colocar(Assento.A, Terraco.TELESCOPIO, Optional.of(Terraco.C2), 0));

      assertThat(desfechos).allMatch(Desfecho::aceito);
      assertThat(desfechos).allMatch(d -> d.visao().versao() == 1);
      assertThat(mesa.a().consultar(Assento.A).versao()).isEqualTo(1);
    }
  }

  @Test
  void pedidosDeAjusteSimultaneosTerminamEmMontagem() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Mesa mesa = mesaRevelada(salas);

      List<Desfecho> desfechos =
          simultaneamente(
              () -> mesa.a().pedirAjuste(Assento.A), () -> mesa.b().pedirAjuste(Assento.B));

      assertThat(desfechos).allMatch(Desfecho::aceito);
      assertThat(desfechos).filteredOn(d -> "MONTANDO".equals(d.visao().fase())).hasSize(1);
      Visao finalA = mesa.a().consultar(Assento.A);
      assertThat(finalA.fase()).isEqualTo("MONTANDO");
      assertThat(finalA.versao()).isEqualTo(5);
      assertThat(finalA.ajuste()).isNull();
    }
  }

  /** Em qualquer ordem, o destino acaba ocupado e nenhum sinal fica apontando para ele. */
  @Test
  void sinalEColocacaoNoDestinoSimultaneos() throws Exception {
    Salas salas = salas(SEM_LIMITE_DE_SALAS);
    for (int i = 0; i < REPETICOES; i++) {
      Mesa mesa = mesaMontando(salas);
      mesa.b().colocar(Assento.B, Terraco.LANTERNA, Optional.of(Terraco.R2), 0);

      List<Desfecho> desfechos =
          simultaneamente(
              () -> mesa.a().sinalizar(Assento.A, Terraco.LANTERNA, Terraco.L2),
              () -> mesa.a().colocar(Assento.A, Terraco.MANTA, Optional.of(Terraco.L2), 1));

      assertThat(desfechos.get(0).erro()).isIn(Optional.empty(), Optional.of(Codigo.LUGAR_OCUPADO));
      assertThat(desfechos.get(1).aceito()).isTrue();
      Visao finalB = mesa.b().consultar(Assento.B);
      assertThat(finalB.pecas()).filteredOn(p -> "L2".equals(lugarDe(p))).hasSize(1);
      assertThat(finalB.sinal()).isNull();
    }
  }

  @Test
  void criacoesSimultaneasRespeitamOLimiteDeSalas() throws Exception {
    int limite = 20;
    int pedidos = 30;
    for (int i = 0; i < REPETICOES / 10; i++) {
      Salas salas = salas(limite);

      List<Desfecho> desfechos =
          simultaneamente(
              IntStream.range(0, pedidos)
                  .<Callable<Visao>>mapToObj(
                      n -> () -> salas.criar(Apelido.de("Ana"), Terraco.CENARIO).visao())
                  .toList());

      assertThat(desfechos).filteredOn(Desfecho::aceito).hasSize(limite);
      assertThat(desfechos)
          .filteredOn(d -> !d.aceito())
          .extracting(Desfecho::erro)
          .containsOnly(Optional.of(Codigo.LIMITE_SALAS));
    }
  }

  private record Mesa(Sala a, Sala b) {}

  private record Desfecho(Visao visao, Optional<Codigo> erro) {
    boolean aceito() {
      return erro.isEmpty();
    }
  }

  private Salas salas(int maximo) {
    LimitesSala padrao = LimitesSala.PADRAO;
    return new Salas(
        relogio,
        new LimitesSala(
            maximo, maximo * 2, padrao.inatividade(), padrao.lapide(), padrao.presenca()));
  }

  private static Mesa mesaMontando(Salas salas) {
    Salas.Entrada criacao = salas.criar(Apelido.de("Ana"), Terraco.CENARIO);
    Salas.Entrada entrada = salas.entrar(criacao.visao().convite(), Apelido.de("Bruno"));
    return new Mesa(
        salas.localizar(Optional.of(criacao.token())).sala(),
        salas.localizar(Optional.of(entrada.token())).sala());
  }

  /** Telescópio C1, banco C2, manta L1, lanterna R1: versão 4. */
  private static Mesa mesaCompleta(Salas salas) {
    Mesa mesa = mesaMontando(salas);
    mesa.a().colocar(Assento.A, Terraco.TELESCOPIO, Optional.of(Terraco.C1), 0);
    mesa.b().colocar(Assento.B, Terraco.BANCO, Optional.of(Terraco.C2), 1);
    mesa.a().colocar(Assento.A, Terraco.MANTA, Optional.of(Terraco.L1), 2);
    mesa.b().colocar(Assento.B, Terraco.LANTERNA, Optional.of(Terraco.R1), 3);
    return mesa;
  }

  /** {@link #mesaCompleta} com as duas confirmações: revelada na versão 4. */
  private static Mesa mesaRevelada(Salas salas) {
    Mesa mesa = mesaCompleta(salas);
    mesa.a().confirmar(Assento.A, true, 4);
    mesa.b().confirmar(Assento.B, true, 4);
    return mesa;
  }

  private static String lugarDe(Visao.VisaoPeca peca) {
    return switch (peca) {
      case Visao.PecaPropria propria -> propria.lugar();
      case Visao.PecaDoParceiro doParceiro -> doParceiro.lugar();
    };
  }

  private List<Desfecho> simultaneamente(Callable<Visao> primeira, Callable<Visao> segunda)
      throws Exception {
    return simultaneamente(List.of(primeira, segunda));
  }

  /** Solta todas as tarefas juntas depois que todas estão prontas para começar. */
  private List<Desfecho> simultaneamente(List<Callable<Visao>> tarefas) throws Exception {
    CountDownLatch prontas = new CountDownLatch(tarefas.size());
    CountDownLatch largada = new CountDownLatch(1);
    List<Future<Visao>> futuros = new ArrayList<>();
    for (Callable<Visao> tarefa : tarefas) {
      futuros.add(
          pool.submit(() -> {
            prontas.countDown();
            largada.await();
            return tarefa.call();
          }));
    }
    prontas.await();
    largada.countDown();
    List<Desfecho> desfechos = new ArrayList<>();
    for (Future<Visao> futuro : futuros) {
      desfechos.add(desfecho(futuro));
    }
    return desfechos;
  }

  private static Desfecho desfecho(Future<Visao> futuro) throws InterruptedException {
    try {
      return new Desfecho(futuro.get(), Optional.empty());
    } catch (ExecutionException e) {
      if (e.getCause() instanceof ErroApi erro) {
        return new Desfecho(null, Optional.of(erro.codigo()));
      }
      throw new AssertionError("Falha inesperada", e.getCause());
    }
  }
}
