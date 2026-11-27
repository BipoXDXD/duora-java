package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.dominio.FinalidadeDaTentativa;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ReservaDeTentativa;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.Tentativa;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Tentativas e aprovação da verificação de idade no Postgres. */
@Component
class VerificacoesJdbc implements RepositorioDeVerificacoes {

  /** A janela do limite por dia: 24 h corridas antes de {@code agora}. */
  private static final Duration JANELA = Duration.parse("P1D");

  private final JdbcClient jdbc;
  private final GeradorDeIds geradorDeIds;

  VerificacoesJdbc(JdbcClient jdbc, GeradorDeIds geradorDeIds) {
    this.jdbc = jdbc;
    this.geradorDeIds = geradorDeIds;
  }

  /** O que a janela da finalidade já tem: quantas tentativas e a mais antiga delas. */
  private record Janela(int quantas, Optional<Instant> maisAntiga) {}

  /**
   * A trava da conta vem antes da contagem: em READ COMMITTED, a contagem é um comando novo depois
   * da espera e vê a tentativa que a reserva concorrente gravou.
   */
  @Override
  public ReservaDeTentativa reservar(
      ContaId conta, FinalidadeDaTentativa finalidade, int limitePorDia, Instant agora) {
    if (limitePorDia < 1) {
      throw new IllegalArgumentException("Limite por dia menor que 1: " + limitePorDia);
    }
    travarConta(conta);
    Janela janela =
        jdbc.sql(
                """
                select count(*) as quantas, min(criada_em) as mais_antiga
                from tentativa_verificacao
                where conta_id = :conta and finalidade = :finalidade and criada_em > :desde
                """)
            .param("conta", conta.valor())
            .param("finalidade", finalidade.name())
            .param("desde", utc(agora.minus(JANELA)))
            .query((rs, linha) ->
                new Janela(
                    rs.getInt("quantas"),
                    Optional.ofNullable(rs.getObject("mais_antiga", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant)))
            .single();
    if (janela.quantas() >= limitePorDia) {
      Instant maisAntiga =
          janela.maisAntiga().orElseThrow(() -> new IllegalStateException("Janela sem tentativa"));
      return new ReservaDeTentativa.Esgotada(maisAntiga.plus(JANELA));
    }
    UUID id = geradorDeIds.novo();
    jdbc.sql(
            """
            insert into tentativa_verificacao (id, conta_id, finalidade, criada_em)
            values (:id, :conta, :finalidade, :agora)
            """)
        .param("id", id)
        .param("conta", conta.valor())
        .param("finalidade", finalidade.name())
        .param("agora", utc(agora))
        .update();
    return new ReservaDeTentativa.Feita(id);
  }

  private void travarConta(ContaId conta) {
    jdbc.sql("select id from conta where id = :id for update")
        .param("id", conta.valor())
        .query(UUID.class)
        .optional()
        .orElseThrow(() -> new IllegalStateException("Conta inexistente: " + conta));
  }

  @Override
  public void associarSessao(UUID tentativa, String sessaoId) {
    int alteradas =
        jdbc.sql(
                """
                update tentativa_verificacao set sessao_id = :sessao
                where id = :id and sessao_id is null
                """)
            .param("sessao", sessaoId)
            .param("id", tentativa)
            .update();
    if (alteradas != 1) {
      throw new IllegalStateException("Tentativa inexistente ou já com sessão: " + tentativa);
    }
  }

  @Override
  public void descartar(UUID tentativa) {
    jdbc.sql("delete from tentativa_verificacao where id = :id and concluida_em is null")
        .param("id", tentativa)
        .update();
  }

  @Override
  public Optional<Tentativa> porSessao(String sessaoId) {
    return jdbc.sql(
            """
            select id, conta_id, finalidade, sessao_id, criada_em, resultado
            from tentativa_verificacao where sessao_id = :sessao
            """)
        .param("sessao", sessaoId)
        .query((rs, linha) -> tentativa(rs))
        .optional();
  }

  /**
   * Em READ COMMITTED, a segunda conclusão concorrente espera o lock da linha e reavalia o WHERE
   * sobre a versão já concluída: não altera nada e devolve {@code false}.
   */
  @Override
  public boolean concluir(UUID tentativa, ResultadoDaVerificacao resultado, Instant agora) {
    return jdbc.sql(
                """
                update tentativa_verificacao set resultado = :resultado, concluida_em = :agora
                where id = :id and concluida_em is null
                """)
            .param("resultado", resultado.name())
            .param("agora", utc(agora))
            .param("id", tentativa)
            .update()
        == 1;
  }

  @Override
  public int contarDesde(ContaId conta, FinalidadeDaTentativa finalidade, Instant desde) {
    return jdbc.sql(
            """
            select count(*) from tentativa_verificacao
            where conta_id = :conta and finalidade = :finalidade and criada_em > :desde
            """)
        .param("conta", conta.valor())
        .param("finalidade", finalidade.name())
        .param("desde", utc(desde))
        .query(Integer.class)
        .single();
  }

  @Override
  public boolean duvidaDeSelfieDesde(ContaId conta, Instant desde) {
    return jdbc.sql(
            """
            select exists (
                select 1 from tentativa_verificacao
                where conta_id = :conta and finalidade = 'SELFIE' and resultado = 'DUVIDA'
                  and criada_em > :desde)
            """)
        .param("conta", conta.valor())
        .param("desde", utc(desde))
        .query(Boolean.class)
        .single();
  }

  @Override
  public void gravarAprovacao(ContaId conta, MetodoDeVerificacao metodo, Instant agora) {
    jdbc.sql(
            """
            insert into verificacao_idade (conta_id, metodo, aprovada_em)
            values (:conta, :metodo, :agora)
            on conflict (conta_id) do nothing
            """)
        .param("conta", conta.valor())
        .param("metodo", metodo.name())
        .param("agora", utc(agora))
        .update();
  }

  @Override
  public boolean aprovada(ContaId conta) {
    return jdbc.sql("select exists (select 1 from verificacao_idade where conta_id = :conta)")
        .param("conta", conta.valor())
        .query(Boolean.class)
        .single();
  }

  @Override
  public void apagarDaConta(ContaId conta) {
    jdbc.sql("delete from tentativa_verificacao where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
    jdbc.sql("delete from verificacao_idade where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }

  private static Tentativa tentativa(ResultSet rs) throws SQLException {
    String resultado = rs.getString("resultado");
    return new Tentativa(
        rs.getObject("id", UUID.class),
        new ContaId(rs.getObject("conta_id", UUID.class)),
        FinalidadeDaTentativa.valueOf(rs.getString("finalidade")),
        rs.getString("sessao_id"),
        rs.getObject("criada_em", OffsetDateTime.class).toInstant(),
        resultado == null ? null : ResultadoDaVerificacao.valueOf(resultado));
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }
}
