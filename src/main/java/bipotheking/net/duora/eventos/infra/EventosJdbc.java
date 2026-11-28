package bipotheking.net.duora.eventos.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.Evento;
import bipotheking.net.duora.eventos.dominio.EventoGravado;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** {@link RepositorioDeEventos} sobre a tabela {@code evento_conta} (V50). */
@Component
class EventosJdbc implements RepositorioDeEventos {

  private final JdbcClient jdbc;
  private final JsonMapper json;

  EventosJdbc(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  /** Trava advisory de transação: some sozinha no commit ou no rollback. */
  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void travarConta(ContaId conta) {
    jdbc.sql("select pg_advisory_xact_lock(hashtextextended(cast(:conta as text), 0))")
        .param("conta", conta.valor())
        .query((rs, linha) -> Boolean.TRUE)
        .single();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public long gravar(ContaId conta, Evento evento, Instant agora) {
    return jdbc.sql(
            """
            insert into evento_conta (conta_id, nome, dados, criado_em)
            values (:conta, :nome, cast(:dados as jsonb), :agora)
            returning seq
            """)
        .param("conta", conta.valor())
        .param("nome", evento.nome())
        .param("dados", json.writeValueAsString(evento.dados()))
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }

  @Override
  public List<EventoGravado> depoisDe(ContaId conta, long seq, int limite) {
    return jdbc.sql(
            """
            select seq, nome, dados::text as dados from evento_conta
            where conta_id = :conta and seq > :seq
            order by seq
            limit :limite
            """)
        .param("conta", conta.valor())
        .param("seq", seq)
        .param("limite", limite)
        .query((rs, linha) ->
            new EventoGravado(rs.getLong("seq"), rs.getString("nome"), rs.getString("dados")))
        .list();
  }

  @Override
  public OptionalLong menorSeq(ContaId conta) {
    return jdbc.sql("select min(seq) from evento_conta where conta_id = :conta")
        .param("conta", conta.valor())
        .query(Long.class)
        .optional()
        .map(OptionalLong::of)
        .orElseGet(OptionalLong::empty);
  }

  @Override
  public int apagarDaConta(ContaId conta) {
    return jdbc.sql("delete from evento_conta where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }
}
