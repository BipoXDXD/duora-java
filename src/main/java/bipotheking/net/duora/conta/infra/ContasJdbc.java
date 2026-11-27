package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Contas no Postgres. Cada linha lida passa de novo pela validação de {@link Email} (construtor,
 * sem normalizar): um valor fora da forma no banco falha alto em vez de ser corrigido em silêncio.
 */
@Component
class ContasJdbc implements RepositorioDeContas {

  private static final String COLUNAS =
      "select id, email, estado, data_nascimento, criada_em, ativada_em from conta";

  private final JdbcClient jdbc;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  ContasJdbc(JdbcClient jdbc, Clock relogio, GeradorDeIds geradorDeIds) {
    this.jdbc = jdbc;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
  }

  @Override
  public ContaId criarPendente(Email email) {
    UUID id = geradorDeIds.novo();
    jdbc.sql(
            """
            insert into conta (id, email, estado, criada_em)
            values (:id, :email, :estado, :agora)
            """)
        .param("id", id)
        .param("email", email.valor())
        .param("estado", EstadoConta.PENDENTE.name())
        .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
        .update();
    return new ContaId(id);
  }

  @Override
  public Optional<Conta> porEmail(Email email) {
    return jdbc.sql(COLUNAS + " where email = :email")
        .param("email", email.valor())
        .query((rs, linha) -> conta(rs))
        .optional();
  }

  @Override
  public Optional<Conta> porId(ContaId id) {
    return jdbc.sql(COLUNAS + " where id = :id")
        .param("id", id.valor())
        .query((rs, linha) -> conta(rs))
        .optional();
  }

  private static Conta conta(ResultSet rs) throws SQLException {
    String email = rs.getString("email");
    return new Conta(
        new ContaId(rs.getObject("id", UUID.class)),
        email == null ? null : new Email(email),
        EstadoConta.valueOf(rs.getString("estado")),
        rs.getObject("data_nascimento", LocalDate.class),
        rs.getObject("criada_em", OffsetDateTime.class).toInstant(),
        instante(rs.getObject("ativada_em", OffsetDateTime.class)));
  }

  private static @Nullable Instant instante(@Nullable OffsetDateTime valor) {
    return valor == null ? null : valor.toInstant();
  }
}
