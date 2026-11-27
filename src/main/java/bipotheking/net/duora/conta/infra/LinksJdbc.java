package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Links de entrada no Postgres. */
@Component
class LinksJdbc implements RepositorioDeLinks {

  private final JdbcClient jdbc;

  LinksJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Trava advisory de transação: some sozinha no commit ou no rollback. */
  @Override
  public void travarEmail(String resumoDoEmail) {
    jdbc.sql("select pg_advisory_xact_lock(hashtext(:resumo))")
        .param("resumo", resumoDoEmail)
        .query((rs, linha) -> Boolean.TRUE)
        .single();
  }

  @Override
  public void invalidarAbertos(Email email, Instant agora) {
    jdbc.sql(
            """
            update link_de_entrada set usado_em = :agora
            where email = :email and usado_em is null
            """)
        .param("agora", utc(agora))
        .param("email", email.valor())
        .update();
  }

  @Override
  public void gravar(UUID id, Email email, byte[] tokenHash, Instant criadoEm, Instant expiraEm) {
    jdbc.sql(
            """
            insert into link_de_entrada (id, email, token_hash, criado_em, expira_em)
            values (:id, :email, :tokenHash, :criadoEm, :expiraEm)
            """)
        .param("id", id)
        .param("email", email.valor())
        .param("tokenHash", tokenHash)
        .param("criadoEm", utc(criadoEm))
        .param("expiraEm", utc(expiraEm))
        .update();
  }

  /**
   * Em READ COMMITTED, o segundo UPDATE concorrente espera o lock da linha e reavalia o WHERE sobre
   * a versão já usada: não acha a linha e devolve vazio.
   */
  @Override
  public Optional<Email> consumir(byte[] tokenHash, Instant agora) {
    return jdbc.sql(
            """
            update link_de_entrada set usado_em = :agora
            where token_hash = :tokenHash and usado_em is null and expira_em > :agora
            returning email
            """)
        .param("agora", utc(agora))
        .param("tokenHash", tokenHash)
        .query((rs, linha) -> new Email(rs.getString("email")))
        .optional();
  }

  @Override
  public int apagarDoEmail(Email email) {
    return jdbc.sql("delete from link_de_entrada where email = :email")
        .param("email", email.valor())
        .update();
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }
}
