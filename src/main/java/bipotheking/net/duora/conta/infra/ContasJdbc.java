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
import java.util.Arrays;
import java.util.List;
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

  /** Os estados de onde a exclusão sai: os mesmos que {@link EstadoConta#disponivel()} aceita. */
  private static final List<String> DISPONIVEIS =
      Arrays.stream(EstadoConta.values()).filter(EstadoConta::disponivel).map(Enum::name).toList();

  private final JdbcClient jdbc;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  ContasJdbc(JdbcClient jdbc, Clock relogio, GeradorDeIds geradorDeIds) {
    this.jdbc = jdbc;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
  }

  @Override
  public Optional<ContaId> criarPendente(Email email) {
    UUID id = geradorDeIds.novo();
    int criadas =
        jdbc.sql(
                """
                insert into conta (id, email, estado, criada_em)
                values (:id, :email, :estado, :agora)
                on conflict (email) do nothing
                """)
            .param("id", id)
            .param("email", email.valor())
            .param("estado", EstadoConta.PENDENTE.name())
            .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
            .update();
    return criadas == 1 ? Optional.of(new ContaId(id)) : Optional.empty();
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

  @Override
  public Optional<Conta> travar(ContaId id) {
    return jdbc.sql(COLUNAS + " where id = :id for update")
        .param("id", id.valor())
        .query((rs, linha) -> conta(rs))
        .optional();
  }

  @Override
  public boolean marcarAguardandoVerificacao(ContaId id, LocalDate nascimento) {
    return jdbc.sql(
                """
                update conta set estado = :aguardando, data_nascimento = :nascimento
                where id = :id and estado = :pendente
                """)
            .param("aguardando", EstadoConta.AGUARDANDO_VERIFICACAO.name())
            .param("nascimento", nascimento)
            .param("id", id.valor())
            .param("pendente", EstadoConta.PENDENTE.name())
            .update()
        == 1;
  }

  @Override
  public boolean excluir(ContaId id, Instant agora) {
    return jdbc.sql(
                """
                update conta
                set estado = :excluida, email = null, data_nascimento = null, excluida_em = :agora
                where id = :id and estado in (:disponiveis)
                """)
            .param("excluida", EstadoConta.EXCLUIDA.name())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .param("id", id.valor())
            .param("disponiveis", DISPONIVEIS)
            .update()
        == 1;
  }

  @Override
  public boolean ativar(ContaId id, Instant agora) {
    return jdbc.sql(
                """
                update conta set estado = :ativa, ativada_em = :agora
                where id = :id and estado = :aguardando
                """)
            .param("ativa", EstadoConta.ATIVA.name())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .param("id", id.valor())
            .param("aguardando", EstadoConta.AGUARDANDO_VERIFICACAO.name())
            .update()
        == 1;
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
