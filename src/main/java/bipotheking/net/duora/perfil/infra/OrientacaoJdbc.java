package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Orientacao;
import bipotheking.net.duora.perfil.dominio.DadosDeOrientacao;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import bipotheking.net.duora.perfil.dominio.RepositorioDeOrientacao;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A tabela {@code orientacao} no Postgres. */
@Component
class OrientacaoJdbc implements RepositorioDeOrientacao {

  /** O nome que o Postgres dá à chave estrangeira de {@code orientacao.conta_id}. */
  static final String EXIGE_PERFIL = "orientacao_conta_id_fkey";

  private final JdbcClient jdbc;

  OrientacaoJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DadosDeOrientacao> buscar(ContaId conta) {
    return jdbc.sql(
            """
            select orientacao, descricao, mostrar, consentida_em
            from orientacao where conta_id = :conta
            """)
        .param("conta", conta.valor())
        .query((rs, linha) -> dados(rs))
        .optional();
  }

  /**
   * Upsert numa instrução. Sem perfil, a chave estrangeira recusa o {@code insert}; o nome dela vem
   * do campo próprio da mensagem do servidor, que o driver mantém mesmo com {@code
   * logServerErrorDetail=false}.
   */
  @Override
  public void substituir(ContaId conta, DadosDeOrientacao dados) {
    try {
      jdbc.sql(
              """
              insert into orientacao (conta_id, orientacao, descricao, mostrar, consentida_em)
              values (:conta, :orientacao, :descricao, :mostrar, :consentidaEm)
              on conflict (conta_id) do update set orientacao = excluded.orientacao,
                  descricao = excluded.descricao, mostrar = excluded.mostrar,
                  consentida_em = excluded.consentida_em
              """)
          .param("conta", conta.valor())
          .param("orientacao", dados.orientacao().name())
          .param("descricao", dados.descricao().map(DescricaoCurta::valor).orElse(null))
          .param("mostrar", dados.mostrar())
          .param("consentidaEm", OffsetDateTime.ofInstant(dados.consentidaEm(), ZoneOffset.UTC))
          .update();
    } catch (DataIntegrityViolationException violacao) {
      if (violouPerfil(violacao)) {
        throw CodigoPerfil.PERFIL_NECESSARIO.erro();
      }
      throw violacao;
    }
  }

  @Override
  public void apagar(ContaId conta) {
    jdbc.sql("delete from orientacao where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }

  private static boolean violouPerfil(DataIntegrityViolationException violacao) {
    for (Throwable causa = violacao; causa != null; causa = causa.getCause()) {
      if (causa instanceof PSQLException psql) {
        ServerErrorMessage mensagem = psql.getServerErrorMessage();
        return mensagem != null && EXIGE_PERFIL.equals(mensagem.getConstraint());
      }
    }
    return false;
  }

  static DadosDeOrientacao dados(ResultSet rs) throws SQLException {
    return new DadosDeOrientacao(
        Orientacao.valueOf(rs.getString("orientacao")),
        Optional.ofNullable(rs.getString("descricao")).map(DescricaoCurta::new),
        rs.getBoolean("mostrar"),
        rs.getObject("consentida_em", OffsetDateTime.class).toInstant());
  }
}
