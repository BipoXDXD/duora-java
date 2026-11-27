package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.ProcessadorDeOutbox;
import bipotheking.net.duora.nucleo.Correlacao;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Types;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Entrega as mensagens pendentes da outbox em lotes. A reserva do lote é uma transação curta com
 * {@code FOR UPDATE SKIP LOCKED}, segura com várias instâncias; o processamento roda fora dela, com
 * o correlation ID da mensagem no MDC. Falha agenda nova tentativa com {@link Backoff}; na décima,
 * a mensagem falha de vez e a carga é apagada. Nunca loga a carga.
 *
 * <p>Pública porque os testes de outros módulos e o e2e chamam {@link #despacharAgora()}.
 */
@Component
public class DespachanteDaOutbox {

  private static final Logger LOG = LoggerFactory.getLogger(DespachanteDaOutbox.class);

  private static final int TAMANHO_DO_LOTE = 5;
  // Cobre o lote inteiro com folga (5 × 15 s de timeout do provedor); depois, volta à fila.
  private static final Duration RESERVA = Duration.ofMinutes(2);
  private static final int MAXIMO_DE_TENTATIVAS = 10;
  private static final int TAMANHO_MAXIMO_DO_ERRO = 200;
  private static final String SEM_PROCESSADOR = "sem-processador";

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;
  private final Map<String, ProcessadorDeOutbox<?>> processadores;
  private final Backoff backoff;
  private final JsonMapper json;

  DespachanteDaOutbox(
      JdbcClient jdbc,
      TransactionTemplate transacao,
      Clock relogio,
      List<ProcessadorDeOutbox<?>> processadores,
      Backoff backoff,
      JsonMapper json,
      MeterRegistry metricas) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
    this.processadores =
        processadores.stream()
            .collect(Collectors.toUnmodifiableMap(ProcessadorDeOutbox::tipo, Function.identity()));
    this.backoff = backoff;
    this.json = json;
    Gauge.builder("duora.outbox.pendentes", jdbc, DespachanteDaOutbox::contarPendentes)
        .description("Mensagens da outbox à espera de entrega")
        .strongReference(true)
        .register(metricas);
    Gauge.builder("duora.outbox.falhas", jdbc, DespachanteDaOutbox::contarFalhas)
        .description("Mensagens da outbox que falharam de vez")
        .strongReference(true)
        .register(metricas);
  }

  /**
   * Reserva um lote de até cinco mensagens vencidas e tenta entregá-las.
   *
   * @return quantas foram enviadas
   */
  public int despacharAgora() {
    OffsetDateTime agora = agora();
    List<Mensagem> lote = Objects.requireNonNull(transacao.execute(status -> reservar(agora)));
    int enviadas = 0;
    for (Mensagem mensagem : lote) {
      if (despachar(mensagem)) {
        enviadas++;
      }
    }
    return enviadas;
  }

  private List<Mensagem> reservar(OffsetDateTime agora) {
    return jdbc.sql(
            """
            update outbox set reservada_ate = :reservadaAte
            where id in (
                select id from outbox
                where carga is not null and proxima_tentativa_em <= :agora
                  and (reservada_ate is null or reservada_ate < :agora)
                order by id
                limit :lote
                for update skip locked)
            returning id, tipo, carga::text as carga, correlation_id, tentativas
            """)
        .param("reservadaAte", agora.plus(RESERVA))
        .param("agora", agora)
        .param("lote", TAMANHO_DO_LOTE)
        .query((rs, linha) ->
            new Mensagem(
                rs.getObject("id", UUID.class),
                rs.getString("tipo"),
                rs.getString("carga"),
                rs.getString("correlation_id"),
                rs.getInt("tentativas")))
        .list();
  }

  /** Processa a mensagem com o correlation ID dela no MDC e devolve se foi enviada. */
  private boolean despachar(Mensagem mensagem) {
    String anterior = MDC.get(Correlacao.CHAVE_MDC);
    colocarNoMdc(mensagem.correlationId());
    try {
      ProcessadorDeOutbox<?> processador = processadores.get(mensagem.tipo());
      if (processador == null) {
        falharDeVez(mensagem, mensagem.tentativas() + 1, SEM_PROCESSADOR, SEM_PROCESSADOR);
        return false;
      }
      try {
        processar(processador, mensagem);
      } catch (RuntimeException erro) {
        registrarFalha(mensagem, erro);
        return false;
      }
      marcarEnviada(mensagem);
      return true;
    } finally {
      colocarNoMdc(anterior);
    }
  }

  private <T> void processar(ProcessadorDeOutbox<T> processador, Mensagem mensagem) {
    T carga = json.readValue(mensagem.carga(), processador.tipoDaCarga());
    processador.processar(carga, mensagem.id());
  }

  private void marcarEnviada(Mensagem mensagem) {
    jdbc.sql(
            """
            update outbox set carga = null, reservada_ate = null, enviada_em = :agora
            where id = :id and carga is not null
            """)
        .param("agora", agora())
        .param("id", mensagem.id())
        .update();
  }

  private void registrarFalha(Mensagem mensagem, RuntimeException erro) {
    int tentativas = mensagem.tentativas() + 1;
    String classe = erro.getClass().getName();
    String resumo = resumir(erro);
    if (tentativas >= MAXIMO_DE_TENTATIVAS) {
      falharDeVez(mensagem, tentativas, classe, resumo);
      return;
    }
    Duration espera = backoff.proxima(mensagem.tentativas());
    jdbc.sql(
            """
            update outbox
            set tentativas = :tentativas, proxima_tentativa_em = :proxima, reservada_ate = null,
                ultimo_erro = :erro
            where id = :id and carga is not null
            """)
        .param("tentativas", tentativas)
        .param("proxima", agora().plus(espera))
        .param("erro", resumo)
        .param("id", mensagem.id())
        .update();
    LOG.warn(
        "Mensagem {} do tipo {} falhou na tentativa {} ({}); nova tentativa em {}",
        mensagem.id(),
        mensagem.tipo(),
        tentativas,
        classe,
        espera);
  }

  /** Apaga a carga e marca a falha definitiva. O log leva só id, tipo e a classe do erro. */
  private void falharDeVez(Mensagem mensagem, int tentativas, String causa, String resumo) {
    jdbc.sql(
            """
            update outbox
            set tentativas = :tentativas, carga = null, reservada_ate = null, falhou_em = :agora,
                ultimo_erro = :erro
            where id = :id and carga is not null
            """)
        .param("tentativas", tentativas)
        .param("agora", agora())
        .param("erro", resumo)
        .param("id", mensagem.id())
        .update();
    LOG.error(
        "Mensagem {} do tipo {} falhou de vez depois de {} tentativas ({})",
        mensagem.id(),
        mensagem.tipo(),
        tentativas,
        causa);
  }

  /** Classe e mensagem da exceção, cortadas: vai para o banco, nunca para o log. */
  private static String resumir(RuntimeException erro) {
    String mensagem = erro.getMessage();
    String resumo =
        mensagem == null ? erro.getClass().getName() : erro.getClass().getName() + ": " + mensagem;
    return resumo.length() <= TAMANHO_MAXIMO_DO_ERRO
        ? resumo
        : resumo.substring(0, TAMANHO_MAXIMO_DO_ERRO);
  }

  private static void colocarNoMdc(@Nullable String correlationId) {
    if (correlationId == null) {
      MDC.remove(Correlacao.CHAVE_MDC);
    } else {
      MDC.put(Correlacao.CHAVE_MDC, correlationId);
    }
  }

  private static double contarPendentes(JdbcClient jdbc) {
    return jdbc.sql("select count(*) from outbox where carga is not null")
        .query(Long.class)
        .single();
  }

  private static double contarFalhas(JdbcClient jdbc) {
    return jdbc.sql(
            """
            select count(*) from outbox
            where falhou_em is not null and ultimo_erro is distinct from :esquecido
            """)
        .param("esquecido", OutboxJdbc.TITULAR_ESQUECIDO, Types.VARCHAR)
        .query(Long.class)
        .single();
  }

  private OffsetDateTime agora() {
    return OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC);
  }

  /** O que o despacho precisa da linha reservada. */
  private record Mensagem(
      UUID id, String tipo, String carga, @Nullable String correlationId, int tentativas) {}
}
