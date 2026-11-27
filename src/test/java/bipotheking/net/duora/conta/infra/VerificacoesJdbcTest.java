package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ConsultaDeContas;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa.Esgotada;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa.Feita;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.Tentativa;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** As tentativas e a aprovação da verificação de idade no Postgres. */
class VerificacoesJdbcTest extends BancoDeTeste {

  private static final FinalidadeDaTentativa SELFIE = FinalidadeDaTentativa.SELFIE;
  private static final FinalidadeDaTentativa DOCUMENTO = FinalidadeDaTentativa.DOCUMENTO;
  private static final Duration UMA_HORA = Duration.ofHours(1);

  @Autowired private RepositorioDeVerificacoes verificacoes;
  @Autowired private ContasDeTeste contas;
  @Autowired private ConsultaDeContas consulta;

  private ContaId conta;

  @BeforeEach
  void criarConta() {
    conta = contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10)).id();
  }

  @Test
  void reservaAbaixoDoLimiteGravaTentativaSemSessao() {
    UUID id = feita(verificacoes.reservar(conta, SELFIE, 3, INICIO));

    Map<String, Object> linha =
        jdbc.sql(
                """
                select conta_id, finalidade, sessao_id, resultado, criada_em, concluida_em
                from tentativa_verificacao where id = :id
                """)
            .param("id", id)
            .query()
            .singleRow();
    assertThat(linha.get("conta_id")).isEqualTo(conta.valor());
    assertThat(linha.get("finalidade")).isEqualTo("SELFIE");
    assertThat(linha.get("sessao_id")).isNull();
    assertThat(linha.get("resultado")).isNull();
    assertThat(linha.get("concluida_em")).isNull();
    assertThat(instante("select criada_em from tentativa_verificacao")).isEqualTo(INICIO);
  }

  @Test
  void reservaNoLimiteDevolveEsgotada() {
    feita(verificacoes.reservar(conta, SELFIE, 3, INICIO.minus(UMA_HORA.multipliedBy(2))));
    feita(verificacoes.reservar(conta, SELFIE, 3, INICIO.minus(UMA_HORA)));
    feita(verificacoes.reservar(conta, SELFIE, 3, INICIO));

    assertThat(verificacoes.reservar(conta, SELFIE, 3, INICIO))
        .isEqualTo(new Esgotada(INICIO.plus(Duration.ofHours(22))));
    assertThat(verificacoes.contarDesde(conta, SELFIE, INICIO.minus(Duration.ofDays(1))))
        .isEqualTo(3);
  }

  @Test
  void tentativaDeMaisDe24hNaoConta() {
    feita(
        verificacoes.reservar(conta, SELFIE, 1, INICIO.minus(Duration.ofDays(1)).minusSeconds(1)));

    assertThat(verificacoes.reservar(conta, SELFIE, 1, INICIO)).isInstanceOf(Feita.class);
  }

  @Test
  void finalidadesContamSeparado() {
    feita(verificacoes.reservar(conta, SELFIE, 1, INICIO));

    assertThat(verificacoes.reservar(conta, DOCUMENTO, 1, INICIO)).isInstanceOf(Feita.class);
    assertThat(verificacoes.contarDesde(conta, SELFIE, INICIO.minus(Duration.ofDays(1)))).isOne();
    assertThat(verificacoes.contarDesde(conta, DOCUMENTO, INICIO.minus(Duration.ofDays(1))))
        .isOne();
  }

  @Test
  void concluirSoUmaVez() {
    UUID id = feita(verificacoes.reservar(conta, SELFIE, 3, INICIO));
    verificacoes.associarSessao(id, "sessao-1");
    Instant depois = INICIO.plusSeconds(30);

    assertThat(verificacoes.concluir(id, ResultadoDaVerificacao.DUVIDA, depois)).isTrue();
    assertThat(verificacoes.concluir(id, ResultadoDaVerificacao.APROVADA, depois.plusSeconds(1)))
        .isFalse();
    assertThat(verificacoes.porSessao("sessao-1"))
        .contains(
            new Tentativa(id, conta, SELFIE, "sessao-1", INICIO, ResultadoDaVerificacao.DUVIDA));
    assertThat(instante("select concluida_em from tentativa_verificacao")).isEqualTo(depois);
  }

  @Test
  void duvidaDeSelfieIgnoraDocumento() {
    UUID documento = feita(verificacoes.reservar(conta, DOCUMENTO, 2, INICIO));
    verificacoes.associarSessao(documento, "sessao-documento");
    verificacoes.concluir(documento, ResultadoDaVerificacao.DUVIDA, INICIO);

    assertThat(verificacoes.duvidaDeSelfieDesde(conta, INICIO.minus(Duration.ofDays(1)))).isFalse();

    UUID selfie = feita(verificacoes.reservar(conta, SELFIE, 3, INICIO));
    verificacoes.associarSessao(selfie, "sessao-selfie");
    verificacoes.concluir(selfie, ResultadoDaVerificacao.DUVIDA, INICIO);

    assertThat(verificacoes.duvidaDeSelfieDesde(conta, INICIO.minus(Duration.ofDays(1)))).isTrue();
  }

  @Test
  void gravarAprovacaoMarcaAprovada() {
    assertThat(verificacoes.aprovada(conta)).isFalse();

    verificacoes.gravarAprovacao(conta, MetodoDeVerificacao.ESTIMATIVA_FACIAL, INICIO);

    assertThat(verificacoes.aprovada(conta)).isTrue();
    assertThat(jdbc.sql("select metodo from verificacao_idade").query(String.class).single())
        .isEqualTo("ESTIMATIVA_FACIAL");
    assertThat(instante("select aprovada_em from verificacao_idade")).isEqualTo(INICIO);
  }

  @Test
  void apagarDaContaLevaTentativasEAprovacao() {
    ContaId outra = contas.criarAtiva().id();
    feita(verificacoes.reservar(conta, SELFIE, 3, INICIO));
    feita(verificacoes.reservar(outra, SELFIE, 3, INICIO));
    verificacoes.gravarAprovacao(conta, MetodoDeVerificacao.DOCUMENTO, INICIO);
    verificacoes.gravarAprovacao(outra, MetodoDeVerificacao.DOCUMENTO, INICIO);

    verificacoes.apagarDaConta(conta);

    assertThat(verificacoes.aprovada(conta)).isFalse();
    assertThat(verificacoes.contarDesde(conta, SELFIE, INICIO.minus(Duration.ofDays(1)))).isZero();
    assertThat(verificacoes.aprovada(outra)).isTrue();
    assertThat(verificacoes.contarDesde(outra, SELFIE, INICIO.minus(Duration.ofDays(1)))).isOne();
  }

  @Test
  void descartarApagaReserva() {
    UUID id = feita(verificacoes.reservar(conta, SELFIE, 1, INICIO));

    verificacoes.descartar(id);

    assertThat(verificacoes.reservar(conta, SELFIE, 1, INICIO)).isInstanceOf(Feita.class);
  }

  @Test
  void consultaDeContasMostraVerificada() {
    assertThat(consulta.porId(conta)).map(ContaResumo::verificada).contains(false);

    verificacoes.gravarAprovacao(conta, MetodoDeVerificacao.ESTIMATIVA_FACIAL, INICIO);

    assertThat(consulta.porId(conta)).map(ContaResumo::verificada).contains(true);
  }

  @Test
  void colunasDaAprovacaoSaoSoTres() {
    assertThat(
            jdbc.sql(
                    """
                    select column_name from information_schema.columns
                    where table_schema = 'public' and table_name = 'verificacao_idade'
                    """)
                .query(String.class)
                .list())
        .containsExactlyInAnyOrder("conta_id", "metodo", "aprovada_em");
  }

  private static UUID feita(ReservaDeTentativa reserva) {
    assertThat(reserva).isInstanceOf(Feita.class);
    return ((Feita) reserva).tentativa();
  }

  private Instant instante(String sql) {
    return jdbc.sql(sql).query(OffsetDateTime.class).single().toInstant();
  }
}
