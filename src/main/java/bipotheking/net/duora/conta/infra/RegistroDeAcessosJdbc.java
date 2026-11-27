package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EventoDeAcesso;
import bipotheking.net.duora.conta.dominio.RepositorioDeAcessos;
import bipotheking.net.duora.nucleo.Origem;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Registros de acesso no Postgres. O IP vai como texto e o banco o converte em {@code inet}: nada
 * de {@code InetAddress.getByName}, que poderia consultar o DNS.
 */
@Component
class RegistroDeAcessosJdbc implements RepositorioDeAcessos {

  private final JdbcClient jdbc;

  RegistroDeAcessosJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void gravar(
      UUID id, Optional<ContaId> conta, Origem origem, Instant instante, EventoDeAcesso evento) {
    jdbc.sql(
            """
            insert into registro_acesso (id, conta_id, ip, porta, instante, evento)
            values (:id, :conta, cast(:ip as inet), :porta, :instante, :evento)
            """)
        .param("id", id)
        .param("conta", conta.map(ContaId::valor).orElse(null), Types.OTHER)
        .param("ip", origem.ip())
        .param("porta", origem.porta())
        .param("instante", OffsetDateTime.ofInstant(instante, ZoneOffset.UTC))
        .param("evento", evento.name())
        .update();
  }
}
