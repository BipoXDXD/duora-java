package bipotheking.net.duora.sala.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.sala.dominio.RepositorioDeHistorico;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** {@link RepositorioDeHistorico} sobre a tabela {@code cenario_jogado} (V52). */
@Component
class HistoricoJdbc implements RepositorioDeHistorico {

  private final JdbcClient jdbc;

  HistoricoJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void registrar(ContaId conta, String cenarioId, Instant agora) {
    jdbc.sql(
            """
            insert into cenario_jogado (conta_id, cenario_id, primeira_vez_em)
            values (:conta, :cenario, :agora)
            on conflict (conta_id, cenario_id) do nothing
            """)
        .param("conta", conta.valor())
        .param("cenario", cenarioId)
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  @Override
  public Set<String> jogados(ContaId conta) {
    return Set.copyOf(
        jdbc.sql("select cenario_id from cenario_jogado where conta_id = :conta")
            .param("conta", conta.valor())
            .query(String.class)
            .list());
  }
}
