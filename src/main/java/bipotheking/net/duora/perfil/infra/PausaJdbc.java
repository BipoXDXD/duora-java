package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.RepositorioDePausa;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A coluna {@code perfil.pausada_em} no Postgres. Não toca em {@code versao}. */
@Component
class PausaJdbc implements RepositorioDePausa {

  private final JdbcClient jdbc;

  PausaJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean pausar(ContaId conta, Instant agora) {
    return jdbc.sql(
                "update perfil set pausada_em = coalesce(pausada_em, :agora)"
                    + " where conta_id = :conta")
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .param("conta", conta.valor())
            .update()
        == 1;
  }

  @Override
  public boolean retomar(ContaId conta) {
    return jdbc.sql("update perfil set pausada_em = null where conta_id = :conta")
            .param("conta", conta.valor())
            .update()
        == 1;
  }

  @Override
  public boolean pausada(ContaId conta) {
    return jdbc.sql(
            "select exists (select 1 from perfil where conta_id = :conta"
                + " and pausada_em is not null)")
        .param("conta", conta.valor())
        .query(Boolean.class)
        .single();
  }
}
