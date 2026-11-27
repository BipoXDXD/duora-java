package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** E-mails bloqueados no Postgres, pela chave HMAC. */
@Component
class BloqueiosJdbc implements RepositorioDeBloqueios {

  private final JdbcClient jdbc;

  BloqueiosJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean bloqueado(byte[] resumo) {
    return jdbc.sql("select exists (select 1 from email_bloqueado where chave_hmac = :resumo)")
        .param("resumo", resumo)
        .query(Boolean.class)
        .single();
  }

  @Override
  public void bloquear(byte[] resumo, String motivo, Instant agora) {
    jdbc.sql(
            """
            insert into email_bloqueado (chave_hmac, motivo, criado_em)
            values (:resumo, :motivo, :agora)
            on conflict (chave_hmac) do nothing
            """)
        .param("resumo", resumo)
        .param("motivo", motivo)
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }
}
