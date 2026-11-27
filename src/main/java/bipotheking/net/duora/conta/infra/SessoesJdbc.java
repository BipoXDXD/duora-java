package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.RepositorioDeSessoes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Sessões no Postgres, achadas pelo resumo do token. */
@Component
class SessoesJdbc implements RepositorioDeSessoes {

  private final JdbcClient jdbc;

  SessoesJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void gravar(UUID id, ContaId conta, byte[] resumo, Instant criadaEm, Instant expiraEm) {
    jdbc.sql(
            """
            insert into sessao (id, conta_id, token_hash, criada_em, expira_em, renovada_em)
            values (:id, :conta, :resumo, :criadaEm, :expiraEm, :criadaEm)
            """)
        .param("id", id)
        .param("conta", conta.valor())
        .param("resumo", resumo)
        .param("criadaEm", utc(criadaEm))
        .param("expiraEm", utc(expiraEm))
        .update();
  }

  @Override
  public Optional<SessaoGravada> porResumo(byte[] resumo) {
    return jdbc.sql(
            """
            select s.id, s.conta_id, c.estado, s.expira_em, s.renovada_em
            from sessao s join conta c on c.id = s.conta_id
            where s.token_hash = :resumo
            """)
        .param("resumo", resumo)
        .query((rs, linha) ->
            new SessaoGravada(
                rs.getObject("id", UUID.class),
                new ContaId(rs.getObject("conta_id", UUID.class)),
                EstadoConta.valueOf(rs.getString("estado")),
                rs.getObject("expira_em", OffsetDateTime.class).toInstant(),
                rs.getObject("renovada_em", OffsetDateTime.class).toInstant()))
        .optional();
  }

  /**
   * Em READ COMMITTED, o segundo UPDATE concorrente espera o lock da linha e reavalia o WHERE sobre
   * a versão já renovada: não acha a linha e devolve 0.
   */
  @Override
  public boolean renovar(UUID id, Instant agora, Instant expiraEm, Instant renovadaAntesDe) {
    return jdbc.sql(
                """
                update sessao set renovada_em = :agora, expira_em = :expiraEm
                where id = :id and renovada_em < :limite
                """)
            .param("agora", utc(agora))
            .param("expiraEm", utc(expiraEm))
            .param("id", id)
            .param("limite", utc(renovadaAntesDe))
            .update()
        == 1;
  }

  @Override
  public void apagar(byte[] resumo) {
    jdbc.sql("delete from sessao where token_hash = :resumo").param("resumo", resumo).update();
  }

  @Override
  public void apagarDaConta(ContaId conta) {
    jdbc.sql("delete from sessao where conta_id = :conta").param("conta", conta.valor()).update();
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }
}
