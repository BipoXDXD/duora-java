package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.dominio.RepositorioDeLocalizacao;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** A tabela {@code localizacao} no Postgres. */
@Component
class LocalizacaoJdbc implements RepositorioDeLocalizacao {

  private final JdbcClient jdbc;

  LocalizacaoJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void consentirEGravar(ContaId conta, CelulaGeohash celula, Instant agora) {
    jdbc.sql(
            """
            insert into localizacao (conta_id, celula, consentida_em, atualizada_em)
            values (:conta, :celula, :agora, :agora)
            on conflict (conta_id) do update
                set celula = excluded.celula, atualizada_em = excluded.atualizada_em
            """)
        .param("conta", conta.valor())
        .param("celula", celula.valor())
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  @Override
  public boolean atualizar(ContaId conta, CelulaGeohash celula, Instant agora) {
    return jdbc.sql(
                """
                update localizacao set celula = :celula, atualizada_em = :agora
                where conta_id = :conta
                """)
            .param("conta", conta.valor())
            .param("celula", celula.valor())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<CelulaGeohash> celula(ContaId conta) {
    return jdbc.sql("select celula from localizacao where conta_id = :conta")
        .param("conta", conta.valor())
        .query((rs, linha) -> new CelulaGeohash(rs.getString("celula")))
        .optional();
  }

  @Override
  public Optional<Instant> atualizadaEm(ContaId conta) {
    return jdbc.sql("select atualizada_em from localizacao where conta_id = :conta")
        .param("conta", conta.valor())
        .query((rs, linha) -> rs.getObject("atualizada_em", OffsetDateTime.class).toInstant())
        .optional();
  }

  @Override
  public void apagar(ContaId conta) {
    jdbc.sql("delete from localizacao where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }
}
