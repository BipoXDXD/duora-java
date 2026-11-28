package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.dominio.DadosDeNamoro;
import bipotheking.net.duora.perfil.dominio.DescricaoCurta;
import bipotheking.net.duora.perfil.dominio.RepositorioDeNamoro;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A tabela {@code namoro} no Postgres. */
@Component
class NamoroJdbc implements RepositorioDeNamoro {

  /** A chave composta que prende os dados de namoro a {@code perfil.quer_namoro} verdadeiro. */
  static final String EXIGE_INTENCAO = "namoro_exige_intencao_fk";

  private final JdbcClient jdbc;

  NamoroJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DadosDeNamoro> buscar(ContaId conta) {
    return jdbc.sql(
            "select genero, descricao_genero, interesses from namoro where conta_id = :conta")
        .param("conta", conta.valor())
        .query((rs, linha) -> dados(rs))
        .optional();
  }

  @Override
  public Optional<Instant> consentidoEm(ContaId conta) {
    return jdbc.sql("select consentido_em from namoro where conta_id = :conta")
        .param("conta", conta.valor())
        .query((rs, linha) -> rs.getObject("consentido_em", OffsetDateTime.class).toInstant())
        .optional();
  }

  /**
   * Upsert numa instrução. Sem a intenção de namoro, a chave estrangeira recusa o {@code insert}; o
   * nome dela vem do campo próprio da mensagem do servidor, que o driver mantém mesmo com {@code
   * logServerErrorDetail=false}.
   */
  @Override
  public void substituir(ContaId conta, DadosDeNamoro dados, Instant agora) {
    try {
      jdbc.sql(
              """
              insert into namoro (conta_id, genero, descricao_genero, interesses, consentido_em)
              values (:conta, :genero, :descricao, :interesses, :agora)
              on conflict (conta_id) do update set genero = excluded.genero,
                  descricao_genero = excluded.descricao_genero,
                  interesses = excluded.interesses, consentido_em = excluded.consentido_em
              """)
          .param("conta", conta.valor())
          .param("genero", dados.genero().name())
          .param("descricao", dados.descricao().map(DescricaoCurta::valor).orElse(null))
          .param("interesses", dados.interesses().stream().map(Genero::name).toArray(String[]::new))
          .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
          .update();
    } catch (DataIntegrityViolationException violacao) {
      if (violouIntencao(violacao)) {
        throw CodigoPerfil.INTENCAO_SEM_NAMORO.erro();
      }
      throw violacao;
    }
  }

  @Override
  public void apagar(ContaId conta) {
    jdbc.sql("delete from namoro where conta_id = :conta").param("conta", conta.valor()).update();
  }

  /** Se a violação é a de {@code namoro_exige_intencao_fk}, lida pelo nome da constraint. */
  static boolean violouIntencao(DataIntegrityViolationException violacao) {
    for (Throwable causa = violacao; causa != null; causa = causa.getCause()) {
      if (causa instanceof PSQLException psql) {
        ServerErrorMessage mensagem = psql.getServerErrorMessage();
        return mensagem != null && EXIGE_INTENCAO.equals(mensagem.getConstraint());
      }
    }
    return false;
  }

  static DadosDeNamoro dados(ResultSet rs) throws SQLException {
    String descricao = rs.getString("descricao_genero");
    String[] interesses = (String[]) rs.getArray("interesses").getArray();
    Set<Genero> generos = EnumSet.noneOf(Genero.class);
    Arrays.stream(interesses).map(Genero::valueOf).forEach(generos::add);
    return new DadosDeNamoro(
        Genero.valueOf(rs.getString("genero")),
        Optional.ofNullable(descricao).map(DescricaoCurta::new),
        generos);
  }
}
