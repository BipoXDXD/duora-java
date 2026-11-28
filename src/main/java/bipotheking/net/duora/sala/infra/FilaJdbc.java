package bipotheking.net.duora.sala.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.sala.dominio.EntradaNaFila;
import bipotheking.net.duora.sala.dominio.RepositorioDaFila;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@link RepositorioDaFila} sobre a tabela {@code fila_espera} (V53). */
@Component
class FilaJdbc implements RepositorioDaFila {

  private static final String COLUNAS =
      "conta_id, entrou_em, de_teste, ausente_desde, encontro_id, cenario_id, pareado_em";

  private final JdbcClient jdbc;

  FilaJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean inserir(ContaId conta, Instant entrouEm, boolean deTeste) {
    return jdbc.sql(
                """
                insert into fila_espera (conta_id, entrou_em, de_teste)
                values (:conta, :entrouEm, :deTeste)
                on conflict (conta_id) do nothing
                """)
            .param("conta", conta.valor())
            .param("entrouEm", OffsetDateTime.ofInstant(entrouEm, ZoneOffset.UTC))
            .param("deTeste", deTeste)
            .update()
        == 1;
  }

  @Override
  public Optional<EntradaNaFila> daConta(ContaId conta) {
    return jdbc.sql("select " + COLUNAS + " from fila_espera where conta_id = :conta")
        .param("conta", conta.valor())
        .query(FilaJdbc::entrada)
        .optional();
  }

  @Override
  public boolean remover(ContaId conta) {
    return jdbc.sql("delete from fila_espera where conta_id = :conta")
            .param("conta", conta.valor())
            .update()
        == 1;
  }

  @Override
  public int posicaoDe(ContaId conta) {
    return jdbc.sql(
            """
            select count(*) from fila_espera f, fila_espera eu
            where eu.conta_id = :conta and eu.encontro_id is null and f.encontro_id is null
              and (f.entrou_em, f.conta_id) <= (eu.entrou_em, eu.conta_id)
            """)
        .param("conta", conta.valor())
        .query(Integer.class)
        .single();
  }

  @Override
  public List<EntradaNaFila> esperandoDesde(Instant limite) {
    return jdbc.sql(
            "select "
                + COLUNAS
                + """
                 from fila_espera
                where encontro_id is null and entrou_em <= :limite
                order by entrou_em, conta_id
                """)
        .param("limite", OffsetDateTime.ofInstant(limite, ZoneOffset.UTC))
        .query(FilaJdbc::entrada)
        .list();
  }

  private static EntradaNaFila entrada(ResultSet rs, int linha) throws SQLException {
    return new EntradaNaFila(
        new ContaId(rs.getObject("conta_id", UUID.class)),
        rs.getObject("entrou_em", OffsetDateTime.class).toInstant(),
        rs.getBoolean("de_teste"),
        instante(rs, "ausente_desde"),
        rs.getString("encontro_id"),
        rs.getString("cenario_id"),
        instante(rs, "pareado_em"));
  }

  private static @Nullable Instant instante(ResultSet rs, String coluna) throws SQLException {
    OffsetDateTime valor = rs.getObject(coluna, OffsetDateTime.class);
    return valor == null ? null : valor.toInstant();
  }
}
