package bipotheking.net.duora.perfil.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.ServicoDeNamoro;
import bipotheking.net.duora.perfil.ServicoDePerfil;
import bipotheking.net.duora.perfil.dominio.Apelido;
import bipotheking.net.duora.perfil.dominio.Bio;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.EdicaoDoPerfil;
import bipotheking.net.duora.perfil.dominio.Intencoes;
import bipotheking.net.duora.perfil.dominio.RepositorioDeNamoro;
import bipotheking.net.duora.perfil.dominio.RepositorioDePerfis;
import bipotheking.net.duora.web.BancoDeTeste;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/** A intenção e os dados de namoro nunca se desencontram, nem com pedidos simultâneos. */
class NamoroConcorrenciaTest extends BancoDeTeste {

  /** Senha padrão do contêiner Postgres do Testcontainers (a URL {@code jdbc:tc:} não a traz). */
  private static final String SENHA_DO_TESTCONTAINERS = "test";

  private static final DadosDeNamoro MULHER_BUSCA_HOMEM =
      DadosDeNamoro.de(Genero.MULHER, null, List.of(Genero.HOMEM));

  @Autowired private ContasDeTeste contas;
  @Autowired private ServicoDePerfil servicoDePerfil;
  @Autowired private ServicoDeNamoro servicoDeNamoro;
  @Autowired private RepositorioDePerfis repositorioDePerfis;
  @Autowired private RepositorioDeNamoro repositorioDeNamoro;
  @Autowired private TransactionTemplate transacao;
  @Autowired private DataSource dataSource;

  private final ExecutorService pool = Executors.newFixedThreadPool(2);

  @AfterEach
  void encerrarPool() {
    pool.shutdownNow();
  }

  @Test
  void trocaDeIntencaoConcorrenteNuncaDeixaNamoroOrfao() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    servicoDePerfil.salvar(conta, null, edicao(Intencao.AMIZADE, Intencao.NAMORO));
    CountDownLatch apagou = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    ServicoDePerfil comPausa =
        new ServicoDePerfil(
            repositorioDePerfis,
            new NamoroQuePausaDepoisDeApagar(repositorioDeNamoro, apagou, liberar),
            transacao,
            relogio);

    Future<Optional<CodigoDeErro>> troca =
        pool.submit(desfecho(() -> comPausa.salvar(conta, 1L, edicao(Intencao.AMIZADE))));
    assertThat(apagou.await(10, TimeUnit.SECONDS)).isTrue();
    Future<Optional<CodigoDeErro>> gravacao =
        pool.submit(desfecho(() -> servicoDeNamoro.salvar(conta, MULHER_BUSCA_HOMEM, true)));
    esperarAte(gravacao, 3);
    liberar.countDown();

    List<Optional<CodigoDeErro>> desfechos = new ArrayList<>();
    desfechos.add(troca.get(10, TimeUnit.SECONDS));
    desfechos.add(gravacao.get(10, TimeUnit.SECONDS));
    assertThat(namoroSemIntencao()).isZero();
    assertThat(desfechos).filteredOn(Optional::isEmpty).hasSize(1);
    assertThat(desfechos)
        .filteredOn(Optional::isPresent)
        .extracting(Optional::orElseThrow)
        .singleElement()
        .isIn(CodigoPerfil.VERSAO_OBSOLETA, CodigoPerfil.INTENCAO_SEM_NAMORO);
  }

  @Test
  void nomeDaConstraintChegaComDetalheDesligado() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    servicoDePerfil.salvar(conta, null, edicao(Intencao.AMIZADE));
    JdbcClient semDetalhe = JdbcClient.create(dataSourceSemDetalhe());

    assertThatThrownBy(() ->
            semDetalhe
                .sql(
                    """
                    insert into namoro (conta_id, genero, interesses, consentido_em)
                    values (:conta, 'MULHER', array['HOMEM'], now())
                    """)
                .param("conta", conta.valor())
                .update())
        .isInstanceOf(DataIntegrityViolationException.class)
        .rootCause()
        .isInstanceOfSatisfying(
            PSQLException.class,
            erro ->
                assertThat(erro.getServerErrorMessage())
                    .isNotNull()
                    .satisfies(mensagem ->
                        assertThat(mensagem.getConstraint())
                            .isEqualTo("namoro_exige_intencao_fk")));
  }

  @Test
  void intencaoSemNamoroComDetalheDesligado() throws Exception {
    ContaId conta = contas.criarAtiva().id();
    servicoDePerfil.salvar(conta, null, edicao(Intencao.AMIZADE));
    NamoroJdbc semDetalhe = new NamoroJdbc(JdbcClient.create(dataSourceSemDetalhe()));

    assertThatThrownBy(() -> semDetalhe.substituir(conta, MULHER_BUSCA_HOMEM, INICIO))
        .isInstanceOfSatisfying(
            ErroDeNegocio.class,
            erro -> assertThat(erro.codigo()).isEqualTo(CodigoPerfil.INTENCAO_SEM_NAMORO));
  }

  /**
   * Mesmo banco do contexto, com o driver escondendo os detalhes do erro (o padrão de produção).
   */
  private DataSource dataSourceSemDetalhe() throws SQLException {
    PGSimpleDataSource semDetalhe = new PGSimpleDataSource();
    try (Connection conexao = dataSource.getConnection()) {
      DatabaseMetaData metadados = conexao.getMetaData();
      semDetalhe.setUrl(metadados.getURL());
      semDetalhe.setUser(metadados.getUserName());
    }
    semDetalhe.setPassword(SENHA_DO_TESTCONTAINERS);
    semDetalhe.setLogServerErrorDetail(false);
    return semDetalhe;
  }

  private long namoroSemIntencao() {
    return jdbc.sql(
            """
            select count(*) from namoro n join perfil p on p.conta_id = n.conta_id
            where not p.quer_namoro
            """)
        .query(Long.class)
        .single();
  }

  private static EdicaoDoPerfil edicao(Intencao... intencoes) {
    return new EdicaoDoPerfil(
        Apelido.de("Bia"), Optional.empty(), new Intencoes(Set.of(intencoes)), Bio.de(null));
  }

  /** Vazio se a chamada passou; o código se ela recusou com um erro de negócio. */
  private static Callable<Optional<CodigoDeErro>> desfecho(Runnable chamada) {
    return () -> {
      try {
        chamada.run();
        return Optional.empty();
      } catch (ErroDeNegocio erro) {
        return Optional.of(erro.codigo());
      }
    };
  }

  /**
   * Dá à gravação a chance de terminar antes de soltar a troca; se ela travar, segue assim mesmo.
   */
  private static void esperarAte(Future<?> tarefa, int segundos) throws InterruptedException {
    try {
      tarefa.get(segundos, TimeUnit.SECONDS);
    } catch (ExecutionException | TimeoutException e) {
      // O desfecho é conferido depois, pelo get da tarefa.
    }
  }

  /** Para a troca de intenção entre o {@code apagar} do namoro e o {@code update} do perfil. */
  private record NamoroQuePausaDepoisDeApagar(
      RepositorioDeNamoro real, CountDownLatch apagou, CountDownLatch liberar)
      implements RepositorioDeNamoro {

    @Override
    public Optional<DadosDeNamoro> buscar(ContaId conta) {
      return real.buscar(conta);
    }

    @Override
    public Optional<Instant> consentidoEm(ContaId conta) {
      return real.consentidoEm(conta);
    }

    @Override
    public void substituir(ContaId conta, DadosDeNamoro dados, Instant agora) {
      real.substituir(conta, dados, agora);
    }

    @Override
    public void apagar(ContaId conta) {
      real.apagar(conta);
      apagou.countDown();
      try {
        if (!liberar.await(10, TimeUnit.SECONDS)) {
          throw new IllegalStateException("A troca não foi liberada a tempo");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e);
      }
    }
  }
}
