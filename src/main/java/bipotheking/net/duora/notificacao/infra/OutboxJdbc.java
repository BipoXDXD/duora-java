package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.sql.Types;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** {@link Outbox} sobre a tabela {@code outbox} (V3). */
@Component
class OutboxJdbc implements Outbox {

  /** {@code ultimo_erro} das mensagens apagadas por {@link #esquecerTitular}. */
  static final String TITULAR_ESQUECIDO = "titular-esquecido";

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  OutboxJdbc(JdbcClient jdbc, JsonMapper json, Clock relogio, GeradorDeIds geradorDeIds) {
    this.jdbc = jdbc;
    this.json = json;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public <T> void registrar(String tipo, String titular, T carga) {
    Objects.requireNonNull(carga, "carga");
    OffsetDateTime agora = agora();
    jdbc.sql(
            """
            insert into outbox
                (id, tipo, titular, carga, correlation_id, criada_em, proxima_tentativa_em)
            values (:id, :tipo, :titular, cast(:carga as jsonb), :correlationId, :agora, :agora)
            """)
        .param("id", geradorDeIds.novo())
        .param("tipo", tipo)
        .param("titular", titular)
        .param("carga", json.writeValueAsString(carga))
        .param("correlationId", MDC.get(Correlacao.CHAVE_MDC), Types.VARCHAR)
        .param("agora", agora)
        .update();
  }

  @Override
  @Transactional
  public int esquecerTitular(String titular) {
    return jdbc.sql(
            """
            update outbox
            set carga = null, reservada_ate = null, falhou_em = :agora, ultimo_erro = :erro
            where titular = :titular and carga is not null
            """)
        .param("agora", agora())
        .param("erro", TITULAR_ESQUECIDO)
        .param("titular", titular)
        .update();
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
  }
}
