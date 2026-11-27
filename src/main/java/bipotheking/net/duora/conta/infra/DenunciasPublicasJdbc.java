package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.DenunciaPublica;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeDenunciasPublicas;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Denúncias públicas no Postgres; a situação nasce {@code PENDENTE} pelo padrão da coluna. */
@Component
class DenunciasPublicasJdbc implements RepositorioDeDenunciasPublicas {

  private final JdbcClient jdbc;

  DenunciasPublicasJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void gravar(UUID id, DenunciaPublica denuncia, Instant criadaEm) {
    @Nullable Email emailContato = denuncia.emailContato();
    jdbc.sql(
            """
            insert into denuncia_publica
                (id, motivo, descricao, referencia, email_contato, criada_em)
            values (:id, :motivo, :descricao, :referencia, :emailContato, :criadaEm)
            """)
        .param("id", id)
        .param("motivo", denuncia.motivo().name())
        .param("descricao", denuncia.descricao())
        .param("referencia", denuncia.referencia())
        .param("emailContato", emailContato == null ? null : emailContato.valor())
        .param("criadaEm", OffsetDateTime.ofInstant(criadaEm, ZoneOffset.UTC))
        .update();
  }
}
