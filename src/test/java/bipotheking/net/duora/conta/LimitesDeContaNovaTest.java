package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Limites diários dos primeiros 7 dias depois da ativação: 200 mensagens, 10 imagens e 3 convites
 * por dia de São Paulo. Cada consumo roda na transação do efeito, como nas Etapas 4 e 5.
 */
class LimitesDeContaNovaTest extends BancoDeTeste {

  private static final Duration ESPERA = Duration.ofSeconds(5);

  @Autowired private LimitesDeContaNova limites;
  @Autowired private ContasDeTeste contas;
  @Autowired private List<PoliticaDeRetencao> politicas;
  @Autowired private PlatformTransactionManager transacoes;

  @Test
  void ducentesimaPrimeiraMensagemNoDiaRecebe429() {
    ContaDeTeste conta = contas.criarAtiva();
    for (int i = 0; i < 200; i++) {
      consumir(conta.id(), TipoDeLimiteNovo.MENSAGEM);
    }

    assertThatThrownBy(() -> consumir(conta.id(), TipoDeLimiteNovo.MENSAGEM))
        .isInstanceOfSatisfying(
            LimiteDeContaNovaExcedido.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoContaNova.LIMITE_DE_CONTA_NOVA));
  }

  @Test
  void quartoConviteRecebe429() {
    ContaDeTeste conta = contas.criarAtiva();
    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 3);

    assertThatThrownBy(() -> consumir(conta.id(), TipoDeLimiteNovo.CONVITE))
        .isInstanceOf(LimiteDeContaNovaExcedido.class);
  }

  @Test
  void cadaTipoTemOProprioContador() {
    ContaDeTeste conta = contas.criarAtiva();
    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 3);

    consumir(conta.id(), TipoDeLimiteNovo.IMAGEM);

    assertThat(usados(conta.id(), TipoDeLimiteNovo.IMAGEM)).isOne();
  }

  @Test
  void retryAfterAteAMeiaNoiteDeSaoPaulo() {
    relogio.ajustar(Instant.parse("2026-11-22T23:00:00Z"));
    ContaDeTeste conta = contas.criarAtiva();
    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 3);

    assertThatThrownBy(() -> consumir(conta.id(), TipoDeLimiteNovo.CONVITE))
        .isInstanceOfSatisfying(
            LimiteDeContaNovaExcedido.class,
            erro -> assertThat(erro.tentarDeNovoEm()).contains(Duration.ofHours(4)));
  }

  @Test
  void contaComSeteDiasNaoTemLimite() {
    ContaDeTeste conta = contas.criarAtiva();
    relogio.avancar(Duration.ofDays(7));

    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 4);

    assertThat(usados(conta.id(), TipoDeLimiteNovo.CONVITE)).isZero();
  }

  @Test
  void contaSemAtivacaoTemLimite() {
    ContaDeTeste conta = contas.criar(EstadoConta.PENDENTE, null);
    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 3);

    assertThatThrownBy(() -> consumir(conta.id(), TipoDeLimiteNovo.CONVITE))
        .isInstanceOf(LimiteDeContaNovaExcedido.class);
  }

  /** A meia-noite de UTC não zera; a de São Paulo (03:00Z) zera. */
  @Test
  void diaNovoEmSaoPauloZera() {
    relogio.ajustar(Instant.parse("2026-11-22T23:30:00Z"));
    ContaDeTeste conta = contas.criarAtiva();
    consumirVarios(conta.id(), TipoDeLimiteNovo.CONVITE, 3);

    relogio.ajustar(Instant.parse("2026-11-23T02:59:00Z"));
    assertThatThrownBy(() -> consumir(conta.id(), TipoDeLimiteNovo.CONVITE))
        .isInstanceOf(LimiteDeContaNovaExcedido.class);

    relogio.ajustar(Instant.parse("2026-11-23T03:00:00Z"));
    consumir(conta.id(), TipoDeLimiteNovo.CONVITE);
    assertThat(usadosNoDia(conta.id(), TipoDeLimiteNovo.CONVITE, LocalDate.of(2026, 11, 23)))
        .isOne();
  }

  /** 20 consumos largam juntos contra o limite de 10 imagens: passam exatamente 10. */
  @Test
  void consumosSimultaneosNaoPassamDoLimite() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    CountDownLatch largada = new CountDownLatch(1);

    int sucessos = 0;
    try (ExecutorService threads = Executors.newFixedThreadPool(20)) {
      List<Future<Boolean>> resultados = new ArrayList<>();
      for (int i = 0; i < 20; i++) {
        resultados.add(
            threads.submit(() -> {
              aguardar(largada);
              return tentarConsumir(conta.id(), TipoDeLimiteNovo.IMAGEM);
            }));
      }
      largada.countDown();
      for (Future<Boolean> resultado : resultados) {
        if (resultado.get()) {
          sucessos++;
        }
      }
    }

    assertThat(sucessos).isEqualTo(10);
    assertThat(usados(conta.id(), TipoDeLimiteNovo.IMAGEM)).isEqualTo(10);
  }

  /**
   * Uma transação gastou 9 das 10 imagens e ainda não fez commit; dois consumos esperam a trava da
   * linha. Depois do commit, o banco reavalia o limite na versão nova: passa um, o outro recebe
   * 429.
   */
  @Test
  void consumoQueEsperaATravaReavaliaOLimite() throws Exception {
    ContaDeTeste conta = contas.criarAtiva();
    CountDownLatch noveGastas = new CountDownLatch(1);
    CountDownLatch soltar = new CountDownLatch(1);

    boolean esperaram;
    int sucessos = 0;
    try (ExecutorService threads = Executors.newFixedThreadPool(3)) {
      Future<?> primeira =
          threads.submit(() ->
              transacao()
                  .executeWithoutResult(status -> {
                    for (int i = 0; i < 9; i++) {
                      limites.consumir(conta.id(), TipoDeLimiteNovo.IMAGEM);
                    }
                    noveGastas.countDown();
                    aguardar(soltar);
                  }));
      List<Future<Boolean>> esperando = new ArrayList<>();
      try {
        assertThat(noveGastas.await(ESPERA.toMillis(), TimeUnit.MILLISECONDS)).isTrue();
        for (int i = 0; i < 2; i++) {
          esperando.add(threads.submit(() -> tentarConsumir(conta.id(), TipoDeLimiteNovo.IMAGEM)));
        }
        esperaram = conexoesEmEspera(2);
      } finally {
        soltar.countDown();
      }
      primeira.get();
      for (Future<Boolean> resultado : esperando) {
        if (resultado.get()) {
          sucessos++;
        }
      }
    }

    assertThat(esperaram).as("os dois consumos esperaram a trava da linha").isTrue();
    assertThat(sucessos).isOne();
    assertThat(usados(conta.id(), TipoDeLimiteNovo.IMAGEM)).isEqualTo(10);
  }

  @Test
  void consumirForaDeTransacaoFalha() {
    ContaDeTeste conta = contas.criarAtiva();

    assertThatThrownBy(() -> limites.consumir(conta.id(), TipoDeLimiteNovo.CONVITE))
        .isInstanceOf(IllegalTransactionStateException.class);
  }

  @Test
  void contaInexistenteFalha() {
    ContaId inexistente = new ContaId(UUID.randomUUID());

    assertThatThrownBy(() -> consumir(inexistente, TipoDeLimiteNovo.CONVITE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** Às 09:00 de São Paulo, o dia de 9 dias atrás acabou há 8 dias e meio; o de 8, há 7 e meio. */
  @Test
  void retencaoApagaDiasCom8DiasEMeio() {
    ContaDeTeste conta = contas.criarAtiva();
    LocalDate hoje = LocalDate.of(2026, 11, 22);
    inserirUso(conta.id(), hoje.minusDays(9));
    inserirUso(conta.id(), hoje.minusDays(8));

    assertThat(usoContaNova().expirar(INICIO.minus(usoContaNova().prazo()))).isOne();
    assertThat(dias(conta.id())).containsExactly(hoje.minusDays(8));
  }

  @Test
  void tamanhoContaOsDiasVencidos() {
    ContaDeTeste conta = contas.criarAtiva();
    LocalDate hoje = LocalDate.of(2026, 11, 22);
    inserirUso(conta.id(), hoje.minusDays(9));
    inserirUso(conta.id(), hoje.minusDays(8));

    assertThat(usoContaNova().tamanho()).isOne();
  }

  @Test
  void nomeEPrazoDaRetencao() {
    assertThat(usoContaNova().nome()).isEqualTo("uso-conta-nova");
    assertThat(usoContaNova().prazo()).isEqualTo(Duration.parse("P8D"));
  }

  private TransactionTemplate transacao() {
    return new TransactionTemplate(transacoes);
  }

  private void consumir(ContaId conta, TipoDeLimiteNovo tipo) {
    transacao().executeWithoutResult(status -> limites.consumir(conta, tipo));
  }

  private void consumirVarios(ContaId conta, TipoDeLimiteNovo tipo, int vezes) {
    for (int i = 0; i < vezes; i++) {
      consumir(conta, tipo);
    }
  }

  /** {@code true} se o consumo passou; {@code false} se recebeu 429. */
  private boolean tentarConsumir(ContaId conta, TipoDeLimiteNovo tipo) {
    try {
      consumir(conta, tipo);
      return true;
    } catch (LimiteDeContaNovaExcedido e) {
      return false;
    }
  }

  private PoliticaDeRetencao usoContaNova() {
    return politicas.stream()
        .filter(p -> p.nome().equals("uso-conta-nova"))
        .findFirst()
        .orElseThrow();
  }

  private void inserirUso(ContaId conta, LocalDate dia) {
    jdbc.sql(
            """
            insert into uso_conta_nova (conta_id, dia, tipo, usados)
            values (:conta, :dia, 'MENSAGEM', 1)
            """)
        .param("conta", conta.valor())
        .param("dia", dia)
        .update();
  }

  private List<LocalDate> dias(ContaId conta) {
    return jdbc.sql("select dia from uso_conta_nova where conta_id = :conta order by dia")
        .param("conta", conta.valor())
        .query(LocalDate.class)
        .list();
  }

  /** O total consumido do tipo, somando todos os dias. */
  private int usados(ContaId conta, TipoDeLimiteNovo tipo) {
    return jdbc.sql(
            "select coalesce(sum(usados), 0) from uso_conta_nova"
                + " where conta_id = :conta and tipo = :tipo")
        .param("conta", conta.valor())
        .param("tipo", tipo.name())
        .query(Integer.class)
        .single();
  }

  private int usadosNoDia(ContaId conta, TipoDeLimiteNovo tipo, LocalDate dia) {
    return jdbc.sql(
            "select usados from uso_conta_nova"
                + " where conta_id = :conta and tipo = :tipo and dia = :dia")
        .param("conta", conta.valor())
        .param("tipo", tipo.name())
        .param("dia", dia)
        .query(Integer.class)
        .single();
  }

  /** Espera {@code quantas} conexões presas na trava de outra transação, até o limite. */
  private boolean conexoesEmEspera(int quantas) throws InterruptedException {
    long limite = System.nanoTime() + ESPERA.toNanos();
    while (System.nanoTime() < limite) {
      long esperando =
          jdbc.sql("select count(*) from pg_locks where locktype = 'transactionid' and not granted")
              .query(Long.class)
              .single();
      if (esperando >= quantas) {
        return true;
      }
      Thread.sleep(10);
    }
    return false;
  }

  private static void aguardar(CountDownLatch trinco) {
    try {
      // Folga maior que a da sondagem da trava, para a falha sair na asserção, não aqui.
      if (!trinco.await(ESPERA.multipliedBy(3).toMillis(), TimeUnit.MILLISECONDS)) {
        throw new IllegalStateException("O teste não soltou a transação a tempo");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
