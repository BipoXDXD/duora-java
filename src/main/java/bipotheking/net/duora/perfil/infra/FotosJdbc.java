package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FotoId;
import bipotheking.net.duora.perfil.FotoPendente;
import bipotheking.net.duora.perfil.dominio.EstadoDaFoto;
import bipotheking.net.duora.perfil.dominio.FotoDePerfil;
import bipotheking.net.duora.perfil.dominio.RepositorioDeFotos;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A tabela {@code foto} no Postgres. */
@Component
class FotosJdbc implements RepositorioDeFotos {

  private static final String COLUNAS =
      "id, conta_id, estado, largura, altura, enviada_em, decidida_em";

  private final JdbcClient jdbc;

  FotosJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void travarConta(ContaId conta) {
    jdbc.sql("select pg_advisory_xact_lock(hashtext('foto:' || cast(:conta as text)))")
        .param("conta", conta.valor())
        .query((rs, linha) -> Boolean.TRUE)
        .single();
  }

  @Override
  public Optional<FotoDePerfil> porId(FotoId id) {
    return jdbc.sql("select " + COLUNAS + " from foto where id = :id")
        .param("id", id.valor())
        .query((rs, linha) -> foto(rs))
        .optional();
  }

  @Override
  public List<FotoDePerfil> daConta(ContaId conta) {
    return jdbc.sql("select " + COLUNAS + " from foto where conta_id = :conta order by enviada_em")
        .param("conta", conta.valor())
        .query((rs, linha) -> foto(rs))
        .list();
  }

  @Override
  public Optional<FotoId> apagarDoEstado(ContaId conta, EstadoDaFoto estado) {
    return jdbc.sql("delete from foto where conta_id = :conta and estado = :estado returning id")
        .param("conta", conta.valor())
        .param("estado", estado.name())
        .query((rs, linha) -> new FotoId(rs.getObject("id", UUID.class)))
        .optional();
  }

  @Override
  public void inserirPendente(FotoDePerfil foto) {
    if (foto.estado() != EstadoDaFoto.PENDENTE) {
      throw new IllegalArgumentException("Só entra foto pendente");
    }
    jdbc.sql(
            """
            insert into foto (id, conta_id, estado, largura, altura, enviada_em, decidida_em)
            values (:id, :conta, 'PENDENTE', :largura, :altura, :enviadaEm, null)
            """)
        .param("id", foto.id().valor())
        .param("conta", foto.conta().valor())
        .param("largura", foto.largura())
        .param("altura", foto.altura())
        .param("enviadaEm", utc(foto.enviadaEm()))
        .update();
  }

  @Override
  public boolean decidir(FotoId id, EstadoDaFoto estado, Instant agora) {
    if (estado == EstadoDaFoto.PENDENTE) {
      throw new IllegalArgumentException("A decisão aprova ou recusa");
    }
    return jdbc.sql(
                """
                update foto set estado = :estado, decidida_em = :agora
                where id = :id and estado = 'PENDENTE'
                """)
            .param("estado", estado.name())
            .param("agora", utc(agora))
            .param("id", id.valor())
            .update()
        == 1;
  }

  @Override
  public List<FotoPendente> pendentes(int limite) {
    return jdbc.sql(
            """
            select id, conta_id, enviada_em from foto where estado = 'PENDENTE'
            order by enviada_em, id limit :limite
            """)
        .param("limite", limite)
        .query((rs, linha) ->
            new FotoPendente(
                new FotoId(rs.getObject("id", UUID.class)),
                new ContaId(rs.getObject("conta_id", UUID.class)),
                rs.getObject("enviada_em", OffsetDateTime.class).toInstant()))
        .list();
  }

  @Override
  public List<FotoId> apagarDaConta(ContaId conta) {
    return jdbc.sql("delete from foto where conta_id = :conta returning id")
        .param("conta", conta.valor())
        .query((rs, linha) -> new FotoId(rs.getObject("id", UUID.class)))
        .list();
  }

  @Override
  public Set<UUID> existentes(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql("select id from foto where id in (:ids)")
            .param("ids", List.copyOf(ids))
            .query((rs, linha) -> rs.getObject("id", UUID.class))
            .list());
  }

  private static FotoDePerfil foto(ResultSet rs) throws SQLException {
    @Nullable OffsetDateTime decididaEm = rs.getObject("decidida_em", OffsetDateTime.class);
    return new FotoDePerfil(
        new FotoId(rs.getObject("id", UUID.class)),
        new ContaId(rs.getObject("conta_id", UUID.class)),
        EstadoDaFoto.valueOf(rs.getString("estado")),
        rs.getInt("largura"),
        rs.getInt("altura"),
        rs.getObject("enviada_em", OffsetDateTime.class).toInstant(),
        decididaEm == null ? null : decididaEm.toInstant());
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }
}
