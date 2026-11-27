package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class SessoesTest extends BancoDeTeste {

  private static final Duration TRINTA_DIAS = Duration.ofDays(30);
  private static final Duration UM_DIA = Duration.ofHours(24);
  private static final Duration UM_SEGUNDO = Duration.ofSeconds(1);

  @Autowired private Sessoes sessoes;
  @Autowired private ContasDeTeste contas;
  @Autowired private TransactionTemplate transacao;

  @Test
  void abrirEValidarDevolveAConta() {
    ContaId conta = contas.criarAtiva().id();

    String token = sessoes.abrir(conta);

    assertThat(sessoes.validar(token)).contains(new SessaoAtiva(conta, EstadoConta.ATIVA, false));
  }

  @Test
  void bancoGuardaSoOResumo() {
    String token = sessoes.abrir(contas.criarAtiva().id());

    Integer comOResumo =
        jdbc.sql("select count(*) from sessao where token_hash = :resumo")
            .param("resumo", Resumos.sha256(token))
            .query(Integer.class)
            .single();
    List<String> tiposDasColunas =
        jdbc.sql("select data_type from information_schema.columns where table_name = 'sessao'")
            .query(String.class)
            .list();
    assertThat(comOResumo).isEqualTo(1);
    assertThat(tiposDasColunas).doesNotContain("text", "character varying");
  }

  @Test
  void expiraAos30DiasExatos() {
    String token = sessoes.abrir(contas.criarAtiva().id());

    relogio.avancar(TRINTA_DIAS);

    assertThat(sessoes.validar(token)).isEmpty();
  }

  @Test
  void valeAte30DiasMenos1s() {
    String token = sessoes.abrir(contas.criarAtiva().id());

    relogio.avancar(TRINTA_DIAS.minus(UM_SEGUNDO));

    assertThat(sessoes.validar(token)).isPresent();
  }

  @Test
  void naoRenovaCom24hExatas() {
    String token = sessoes.abrir(contas.criarAtiva().id());
    relogio.avancar(UM_DIA);

    Optional<SessaoAtiva> sessao = sessoes.validar(token);

    assertThat(sessao).map(SessaoAtiva::renovada).contains(false);
    assertThat(renovadaEm(token)).isEqualTo(utc(INICIO));
    assertThat(expiraEm(token)).isEqualTo(utc(INICIO.plus(TRINTA_DIAS)));
  }

  @Test
  void renovaCom24hE1s() {
    String token = sessoes.abrir(contas.criarAtiva().id());
    relogio.avancar(UM_DIA.plus(UM_SEGUNDO));

    Optional<SessaoAtiva> sessao = sessoes.validar(token);

    assertThat(sessao).map(SessaoAtiva::renovada).contains(true);
    assertThat(renovadaEm(token)).isEqualTo(utc(relogio.instant()));
    assertThat(expiraEm(token)).isEqualTo(utc(relogio.instant().plus(TRINTA_DIAS)));
  }

  @Test
  void encerrarInvalida() {
    String token = sessoes.abrir(contas.criarAtiva().id());

    sessoes.encerrar(token);

    assertThat(sessoes.validar(token)).isEmpty();
  }

  @Test
  void revogarTodasSoDaConta() {
    ContasDeTeste.ContaDeTeste a = contas.criarAtiva();
    String segundaDeA = sessoes.abrir(a.id());
    ContasDeTeste.ContaDeTeste b = contas.criarAtiva();

    sessoes.revogarTodas(a.id());

    assertThat(sessoes.validar(token(a))).isEmpty();
    assertThat(sessoes.validar(segundaDeA)).isEmpty();
    assertThat(sessoes.validar(token(b))).isPresent();
  }

  /**
   * Força a corrida: o teste segura o lock da linha; as duas validações leem a sessão ainda velha e
   * ficam presas no UPDATE. Só quando as duas esperam o lock o teste o solta.
   */
  @Test
  void renovacoesSimultaneasGravamUmaVez() {
    String token = sessoes.abrir(contas.criarAtiva().id());
    relogio.avancar(UM_DIA.plus(UM_SEGUNDO));

    List<Boolean> renovadas;
    try (ExecutorService duas = Executors.newFixedThreadPool(2)) {
      List<Future<Boolean>> validacoes =
          transacao.execute(status -> {
            travarSessao(token);
            List<Future<Boolean>> emCurso =
                List.of(duas.submit(() -> renovou(token)), duas.submit(() -> renovou(token)));
            esperarUpdatesNoLock(2);
            return emCurso;
          });
      renovadas = validacoes.stream().map(SessoesTest::resultado).toList();
    }

    assertThat(renovadas).containsExactlyInAnyOrder(true, false);
  }

  private boolean renovou(String token) {
    return sessoes.validar(token).orElseThrow().renovada();
  }

  private void travarSessao(String token) {
    jdbc.sql("select id from sessao where token_hash = :resumo for update")
        .param("resumo", Resumos.sha256(token))
        .query(UUID.class)
        .single();
  }

  /** Espera até {@code quantos} UPDATEs em {@code sessao} aguardarem lock em outras conexões. */
  private void esperarUpdatesNoLock(int quantos) {
    Instant limite = Instant.now().plusSeconds(10);
    while (updatesNoLock() < quantos) {
      assertThat(Instant.now()).as("UPDATEs presos no lock").isBefore(limite);
      Thread.onSpinWait();
    }
  }

  private int updatesNoLock() {
    // pg_stat_activity é fotografada uma vez por transação: limpa a foto a cada leitura.
    jdbc.sql("select pg_stat_clear_snapshot()").query((rs, linha) -> 0).single();
    return jdbc.sql(
            """
            select count(*) from pg_stat_activity
            where pid <> pg_backend_pid() and wait_event_type = 'Lock'
              and query like '%update sessao set renovada_em%'
            """)
        .query(Integer.class)
        .single();
  }

  private static boolean resultado(Future<Boolean> validacao) {
    try {
      return validacao.get(10, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String token(ContasDeTeste.ContaDeTeste conta) {
    return conta.cookie().substring("duora_sessao=".length());
  }

  private OffsetDateTime renovadaEm(String token) {
    return coluna("renovada_em", token);
  }

  private OffsetDateTime expiraEm(String token) {
    return coluna("expira_em", token);
  }

  private OffsetDateTime coluna(String nome, String token) {
    return jdbc.sql("select " + nome + " from sessao where token_hash = :resumo")
        .param("resumo", Resumos.sha256(token))
        .query((rs, linha) -> rs.getObject(1, OffsetDateTime.class))
        .single();
  }

  private static OffsetDateTime utc(Instant instante) {
    return instante.atOffset(ZoneOffset.UTC);
  }
}
