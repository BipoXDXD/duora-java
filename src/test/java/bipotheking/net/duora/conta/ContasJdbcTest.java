package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;

class ContasJdbcTest extends BancoDeTeste {

  private static final Email EMAIL = new Email("ana@teste.duora.invalid");
  private static final String OUTRO_EMAIL = "bia@teste.duora.invalid";
  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final OffsetDateTime AGORA = INICIO.atOffset(ZoneOffset.UTC);

  @Autowired private RepositorioDeContas repositorio;
  @Autowired private ConsultaDeContas consulta;
  @Autowired private ContasDeTeste contas;
  @Autowired private GeradorDeIds geradorDeIds;

  @Test
  void emailUnicoNoBanco() {
    repositorio.criarPendente(EMAIL);

    assertThatThrownBy(() -> inserir(EstadoConta.PENDENTE, EMAIL.valor(), null, null, null))
        .isInstanceOf(DuplicateKeyException.class);
  }

  /** Duas confirmações do mesmo e-mail: a segunda não grava nada nem estoura o unique. */
  @Test
  void criarPendenteDeEmailComContaVemVazio() {
    ContaId primeira = repositorio.criarPendente(EMAIL).orElseThrow();

    assertThat(repositorio.criarPendente(EMAIL)).isEmpty();
    assertThat(repositorio.porEmail(EMAIL)).map(Conta::id).contains(primeira);
  }

  /**
   * O PgJDBC poria {@code Detail: Key (email)=(...)} na mensagem, e o tratamento de erros loga a
   * exceção inesperada: o e-mail iria para o log. O datasource desliga esse detalhe.
   */
  @Test
  void violacaoDeUnicoNaoPoeOEmailNaMensagem() {
    repositorio.criarPendente(EMAIL);

    Throwable erro =
        catchThrowable(() -> inserir(EstadoConta.PENDENTE, EMAIL.valor(), null, null, null));

    assertThat(erro).isInstanceOf(DuplicateKeyException.class);
    for (Throwable causa = erro; causa != null; causa = causa.getCause()) {
      assertThat(String.valueOf(causa.getMessage())).doesNotContain(EMAIL.valor());
    }
  }

  @Test
  void excluidaSemEmailNaoColide() {
    inserir(EstadoConta.EXCLUIDA, null, null, null, AGORA);
    inserir(EstadoConta.EXCLUIDA, null, null, null, AGORA);

    Integer excluidas =
        jdbc.sql("select count(*) from conta where estado = 'EXCLUIDA' and email is null")
            .query(Integer.class)
            .single();
    assertThat(excluidas).isEqualTo(2);
  }

  @Test
  void checkRecusaAtivaSemNascimento() {
    assertThatThrownBy(() -> inserir(EstadoConta.ATIVA, OUTRO_EMAIL, null, AGORA, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checkRecusaAtivaSemAtivadaEm() {
    assertThatThrownBy(() -> inserir(EstadoConta.ATIVA, OUTRO_EMAIL, NASCIMENTO, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void checkRecusaExcluidaSemExcluidaEm() {
    assertThatThrownBy(() -> inserir(EstadoConta.EXCLUIDA, null, null, null, null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  /** A lápide da conta excluída não guarda a data de nascimento. */
  @Test
  void checkRecusaExcluidaComNascimento() {
    assertThatThrownBy(() -> inserir(EstadoConta.EXCLUIDA, null, NASCIMENTO, null, AGORA))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("conta_excluida_sem_nascimento_check");
  }

  @Test
  void checkRecusaExcluidaEmForaDeExcluida() {
    assertThatThrownBy(() -> inserir(EstadoConta.PENDENTE, OUTRO_EMAIL, null, null, AGORA))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void porIdDevolveResumo() {
    ContaId id = contas.criarAtiva().id();

    assertThat(consulta.porId(id))
        .contains(new ContaResumo(id, EstadoConta.ATIVA, NASCIMENTO, false, INICIO, true));
  }

  @Test
  void porEmailDevolveAContaPendente() {
    ContaId id = repositorio.criarPendente(EMAIL).orElseThrow();

    assertThat(repositorio.porEmail(EMAIL))
        .contains(new Conta(id, EMAIL, EstadoConta.PENDENTE, null, INICIO, null));
  }

  @Test
  void porIdDesconhecidoVemVazio() {
    ContaId desconhecida = new ContaId(geradorDeIds.novo());

    assertThat(repositorio.porId(desconhecida)).isEmpty();
    assertThat(consulta.porId(desconhecida)).isEmpty();
  }

  /** Repetir a exclusão não desloca o prazo de retenção, que conta de {@code excluida_em}. */
  @Test
  void excluirDeNovoMantemOInstanteDaPrimeiraExclusao() {
    ContaId conta = repositorio.criarPendente(EMAIL).orElseThrow();
    repositorio.excluir(conta, INICIO);

    repositorio.excluir(conta, INICIO.plus(Duration.ofDays(1)));

    assertThat(
            jdbc.sql("select excluida_em from conta where id = :id")
                .param("id", conta.valor())
                .query(OffsetDateTime.class)
                .single()
                .toInstant())
        .isEqualTo(INICIO);
  }

  /** Excluir a suspensa ou a banida liberaria o e-mail para uma conta nova. */
  @ParameterizedTest
  @EnumSource(
      value = EstadoConta.class,
      names = {"SUSPENSA", "BANIDA"})
  void excluirNaoSaiDeContaIndisponivel(EstadoConta estado) {
    ContaId conta = repositorio.criarPendente(EMAIL).orElseThrow();
    jdbc.sql(
            """
            update conta set estado = :estado, data_nascimento = :nascimento,
                ativada_em = criada_em
            where id = :id
            """)
        .param("estado", estado.name())
        .param("nascimento", LocalDate.of(1990, 5, 10))
        .param("id", conta.valor())
        .update();

    assertThat(repositorio.excluir(conta, INICIO)).isFalse();

    assertThat(repositorio.porId(conta))
        .hasValueSatisfying(linha -> {
          assertThat(linha.estado()).isEqualTo(estado);
          assertThat(linha.email()).isEqualTo(EMAIL);
        });
  }

  /** A ativação só sai de {@code AGUARDANDO_VERIFICACAO}: nem da pendente, nem duas vezes. */
  @Test
  void ativarSoSaiDeAguardando() {
    ContaId pendente = contas.criar(EstadoConta.PENDENTE, null).id();
    ContaId aguardando = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, NASCIMENTO).id();

    assertThat(repositorio.ativar(pendente, INICIO)).isFalse();
    assertThat(repositorio.ativar(aguardando, INICIO)).isTrue();
    assertThat(repositorio.ativar(aguardando, INICIO.plus(Duration.ofHours(1)))).isFalse();

    assertThat(repositorio.porId(pendente)).map(Conta::estado).contains(EstadoConta.PENDENTE);
    assertThat(repositorio.porId(aguardando))
        .hasValueSatisfying(conta -> {
          assertThat(conta.estado()).isEqualTo(EstadoConta.ATIVA);
          assertThat(conta.ativadaEm()).isEqualTo(INICIO);
        });
  }

  /** Insere direto, para testar os checks do esquema: cada caso viola uma regra só. */
  private void inserir(
      EstadoConta estado,
      @Nullable String email,
      @Nullable LocalDate dataDeNascimento,
      @Nullable OffsetDateTime ativadaEm,
      @Nullable OffsetDateTime excluidaEm) {
    jdbc.sql(
            """
            insert into conta
                (id, email, estado, data_nascimento, criada_em, ativada_em, excluida_em)
            values (:id, :email, :estado, :nascimento, :agora, :ativadaEm, :excluidaEm)
            """)
        .param("id", geradorDeIds.novo())
        .param("email", email)
        .param("estado", estado.name())
        .param("nascimento", dataDeNascimento)
        .param("agora", AGORA)
        .param("ativadaEm", ativadaEm)
        .param("excluidaEm", excluidaEm)
        .update();
  }
}
