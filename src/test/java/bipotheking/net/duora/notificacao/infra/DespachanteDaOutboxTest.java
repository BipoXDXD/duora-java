package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.notificacao.infra.ProcessadorFalso.CargaFalsa;
import bipotheking.net.duora.notificacao.infra.ProcessadorFalso.Chamada;
import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.web.BancoDeTeste;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(OutputCaptureExtension.class)
class DespachanteDaOutboxTest extends BancoDeTeste {

  private static final String CANARIO = "canario-da-carga-7f3a@exemplo.invalid";
  private static final Duration DEPOIS_DO_TETO = Duration.ofMinutes(16);

  @Autowired private Outbox outbox;
  @Autowired private TransactionTemplate transacao;
  @Autowired private JsonMapper json;
  @Autowired private ApplicationContext contexto;
  @Autowired private GeradorDeIds geradorDeIds;

  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @AfterEach
  void limparMdc() {
    MDC.remove(Correlacao.CHAVE_MDC);
  }

  /** Jitter neutro (fator 1,0): a 1ª espera é de 2 s exatos. */
  private DespachanteDaOutbox despachante(ProcessadorFalso falso) {
    return new DespachanteDaOutbox(
        jdbc,
        transacao,
        relogio,
        geradorDeIds,
        List.of(falso),
        new Backoff(meio()),
        json,
        metricas);
  }

  private UUID registrar(String tipo, String titular) {
    transacao.executeWithoutResult(
        status -> outbox.registrar(tipo, titular, new CargaFalsa(CANARIO, "oi")));
    return jdbc.sql("select id from outbox order by id desc limit 1").query(UUID.class).single();
  }

  private UUID registrar() {
    return registrar(ProcessadorFalso.TIPO, "titular-a");
  }

  private Map<String, Object> linha(UUID id) {
    return jdbc.sql(
            """
            select carga::text as carga, tentativas, enviada_em, falhou_em, reservada_ate,
                   ultimo_erro
            from outbox where id = :id
            """)
        .param("id", id)
        .query()
        .singleRow();
  }

  private static OffsetDateTime utc(Instant instante) {
    return OffsetDateTime.ofInstant(instante, ZoneOffset.UTC);
  }

  @Test
  void enviaMarcaEApagaACarga() {
    ProcessadorFalso falso = new ProcessadorFalso();
    UUID id = registrar();

    int enviadas = despachante(falso).despacharAgora();

    assertThat(enviadas).isEqualTo(1);
    assertThat(falso.chamadas())
        .extracting(Chamada::idDaMensagem, Chamada::carga)
        .containsExactly(tuple(id, new CargaFalsa(CANARIO, "oi")));
    var linha = linha(id);
    assertThat(((Timestamp) linha.get("enviada_em")).toInstant()).isEqualTo(INICIO);
    assertThat(linha.get("carga")).isNull();
    assertThat(linha.get("reservada_ate")).isNull();
  }

  @Test
  void falhaTentaDeNovoComAMesmaChave() {
    ProcessadorFalso falso = new ProcessadorFalso(1);
    DespachanteDaOutbox despachante = despachante(falso);
    UUID id = registrar();

    assertThat(despachante.despacharAgora()).isZero();
    var depoisDaFalha = linha(id);
    assertThat(depoisDaFalha.get("tentativas")).isEqualTo(1);
    assertThat(depoisDaFalha.get("reservada_ate")).isNull();
    assertThat((String) depoisDaFalha.get("ultimo_erro"))
        .isEqualTo("java.lang.IllegalStateException: provedor fora do ar");

    relogio.avancar(Duration.ofMillis(1999));
    assertThat(despachante.despacharAgora()).isZero();
    assertThat(falso.chamadas()).hasSize(1);

    relogio.avancar(Duration.ofMillis(1));
    assertThat(despachante.despacharAgora()).isEqualTo(1);
    assertThat(falso.chamadas()).extracting(Chamada::idDaMensagem).containsExactly(id, id);
    assertThat(linha(id).get("enviada_em")).isNotNull();
  }

  @Test
  void decimaFalhaMarcaFalhaSemLogarACarga(CapturedOutput saida) {
    ProcessadorFalso falso = new ProcessadorFalso(Integer.MAX_VALUE);
    DespachanteDaOutbox despachante = despachante(falso);
    UUID id = registrar();

    for (int tentativa = 1; tentativa <= 10; tentativa++) {
      assertThat(linha(id).get("falhou_em")).isNull();
      despachante.despacharAgora();
      relogio.avancar(DEPOIS_DO_TETO);
    }

    assertThat(falso.chamadas()).hasSize(10);
    var linha = linha(id);
    assertThat(linha.get("tentativas")).isEqualTo(10);
    assertThat(((Timestamp) linha.get("falhou_em")).toInstant())
        .isEqualTo(INICIO.plus(DEPOIS_DO_TETO.multipliedBy(9)));
    assertThat(linha.get("carga")).isNull();
    assertThat(despachante.despacharAgora()).isZero();
    assertThat(falso.chamadas()).hasSize(10);
    String erro = saida.getOut().lines().filter(l -> l.contains("ERROR")).findFirst().orElseThrow();
    assertThat(erro).contains(id.toString()).contains(ProcessadorFalso.TIPO);
    assertThat(saida.getAll()).doesNotContain(CANARIO);
  }

  @Test
  void tipoSemProcessadorFalhaDeVez() {
    ProcessadorFalso falso = new ProcessadorFalso();
    UUID id = registrar("tipo-desconhecido", "titular-a");

    assertThat(despachante(falso).despacharAgora()).isZero();

    var linha = linha(id);
    assertThat(linha.get("falhou_em")).isNotNull();
    assertThat(linha.get("carga")).isNull();
    assertThat(falso.chamadas()).isEmpty();
  }

  @Test
  void loteDeCinco() {
    for (int i = 0; i < 7; i++) {
      registrar();
    }
    DespachanteDaOutbox despachante = despachante(new ProcessadorFalso());

    assertThat(despachante.despacharAgora()).isEqualTo(5);
    assertThat(despachante.despacharAgora()).isEqualTo(2);
  }

  @Test
  void reservaImpedeEnvioDuplo() throws Exception {
    for (int i = 0; i < 3; i++) {
      registrar();
    }
    CountDownLatch processando = new CountDownLatch(1);
    CountDownLatch liberar = new CountDownLatch(1);
    AtomicBoolean primeira = new AtomicBoolean(true);
    ProcessadorFalso falso =
        ProcessadorFalso.comGancho(() -> {
          if (primeira.getAndSet(false)) {
            processando.countDown();
            aguardar(liberar);
          }
        });
    DespachanteDaOutbox despachante = despachante(falso);
    int segundo;
    int primeiro;
    try (ExecutorService thread = Executors.newSingleThreadExecutor()) {
      Future<Integer> emAndamento = thread.submit(despachante::despacharAgora);
      assertThat(processando.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        // O lote do primeiro já foi reservado e a transação da reserva já fechou: sem locks.
        segundo = despachante.despacharAgora();
      } finally {
        liberar.countDown();
      }
      primeiro = emAndamento.get(5, TimeUnit.SECONDS);
    }

    assertThat(segundo).isZero();
    assertThat(primeiro).isEqualTo(3);
    assertThat(falso.chamadas())
        .extracting(Chamada::idDaMensagem)
        .hasSize(3)
        .doesNotHaveDuplicates();
  }

  @Test
  void enviadaDepoisDeFalhaLimpaUltimoErro() {
    DespachanteDaOutbox despachante = despachante(new ProcessadorFalso(1));
    UUID id = registrar();
    despachante.despacharAgora();
    relogio.avancar(Duration.ofSeconds(2));

    assertThat(despachante.despacharAgora()).isEqualTo(1);

    assertThat(linha(id).get("ultimo_erro")).isNull();
  }

  @Test
  void ultimoErroMascaraEmail() {
    UUID id = registrar();

    despachante(ProcessadorFalso.falhandoCom("destinatário ana.souza+x@exemplo.invalid recusado"))
        .despacharAgora();

    assertThat(linha(id).get("ultimo_erro"))
        .isEqualTo("java.lang.IllegalStateException: destinatário ***@*** recusado");
  }

  @Test
  void cargaIlegivelFalhaDeVez() {
    ProcessadorFalso falso = new ProcessadorFalso();
    UUID id = registrar();
    jdbc.sql(
            """
            update outbox set carga = '{"para":"x","texto":"y","campoNovo":1}'::jsonb
            where id = :id
            """)
        .param("id", id)
        .update();

    assertThat(despachante(falso).despacharAgora()).isZero();

    var linha = linha(id);
    assertThat(linha.get("falhou_em")).isNotNull();
    assertThat(linha.get("carga")).isNull();
    assertThat(linha.get("tentativas")).isEqualTo(1);
    assertThat(falso.chamadas()).isEmpty();
  }

  @Test
  void despacharDentroDeTransacaoFalha() {
    ProcessadorFalso falso = new ProcessadorFalso();
    DespachanteDaOutbox despachante = despachante(falso);
    UUID id = registrar();

    assertThatThrownBy(() -> transacao.executeWithoutResult(s -> despachante.despacharAgora()))
        .isInstanceOf(IllegalStateException.class);

    assertThat(falso.chamadas()).isEmpty();
    assertThat(linha(id).get("reservada_ate")).isNull();
  }

  @Test
  void semCorrelationIdGeraUmNovo() {
    registrar();
    ProcessadorFalso falso = new ProcessadorFalso();

    despachante(falso).despacharAgora();

    assertThat(falso.chamadas())
        .singleElement()
        .extracting(Chamada::correlationId)
        .satisfies(id -> assertThat(UUID.fromString(String.valueOf(id))).isNotNull());
    assertThat(MDC.get(Correlacao.CHAVE_MDC)).isNull();
  }

  @Test
  void reservaVencidaVoltaAFila() {
    ProcessadorFalso falso = new ProcessadorFalso();
    DespachanteDaOutbox despachante = despachante(falso);
    UUID id = registrar();
    jdbc.sql("update outbox set reservada_ate = :agora where id = :id")
        .param("agora", utc(INICIO))
        .param("id", id)
        .update();

    assertThat(despachante.despacharAgora()).isZero();
    assertThat(falso.chamadas()).isEmpty();

    relogio.avancar(Duration.ofSeconds(1));
    assertThat(despachante.despacharAgora()).isEqualTo(1);
  }

  @Test
  void correlationIdDaMensagemNoMdc() {
    MDC.put(Correlacao.CHAVE_MDC, "req-da-mensagem");
    registrar();
    MDC.put(Correlacao.CHAVE_MDC, "req-do-despacho");
    ProcessadorFalso falso = new ProcessadorFalso();

    despachante(falso).despacharAgora();

    assertThat(falso.chamadas())
        .extracting(Chamada::correlationId)
        .containsExactly("req-da-mensagem");
    assertThat(MDC.get(Correlacao.CHAVE_MDC)).isEqualTo("req-do-despacho");
  }

  @Test
  void mdcLimpoDepoisDeFalha() {
    MDC.put(Correlacao.CHAVE_MDC, "req-da-mensagem");
    registrar();
    MDC.remove(Correlacao.CHAVE_MDC);
    ProcessadorFalso falso = new ProcessadorFalso(1);

    despachante(falso).despacharAgora();

    assertThat(falso.chamadas())
        .extracting(Chamada::correlationId)
        .containsExactly("req-da-mensagem");
    assertThat(MDC.get(Correlacao.CHAVE_MDC)).isNull();
  }

  @Test
  void gaugesRefletemFila() {
    registrar();
    registrar();
    registrar("tipo-desconhecido", "titular-a");
    DespachanteDaOutbox despachante = despachante(new ProcessadorFalso(Integer.MAX_VALUE));
    jdbc.sql("update outbox set proxima_tentativa_em = :depois where tipo = :tipo")
        .param("depois", utc(INICIO.plusSeconds(60)))
        .param("tipo", ProcessadorFalso.TIPO)
        .update();
    despachante.despacharAgora();

    assertThat(metricas.get("duora.outbox.pendentes").gauge().value()).isEqualTo(2);
    assertThat(metricas.get("duora.outbox.falhas").gauge().value()).isEqualTo(1);
  }

  @Test
  void titularEsquecidoNaoContaComoFalha() {
    registrar();
    DespachanteDaOutbox despachante = despachante(new ProcessadorFalso());

    outbox.esquecerTitular("titular-a");

    assertThat(despachante.despacharAgora()).isZero();
    assertThat(metricas.get("duora.outbox.pendentes").gauge().value()).isZero();
    assertThat(metricas.get("duora.outbox.falhas").gauge().value()).isZero();
  }

  @Test
  void agendamentoDesligadoNoTeste() {
    assertThat(contexto.getBeansOfType(AgendamentoDaOutbox.class)).isEmpty();
    assertThat(contexto.getBeansOfType(DespachanteDaOutbox.class)).hasSize(1);
  }

  private static void aguardar(CountDownLatch trava) {
    try {
      if (!trava.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("A trava não abriu a tempo");
      }
    } catch (InterruptedException erro) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(erro);
    }
  }

  /** {@code nextDouble} = 0,5, fator de jitter 1,0. */
  private static RandomGenerator meio() {
    return new RandomGenerator() {
      @Override
      public long nextLong() {
        throw new UnsupportedOperationException();
      }

      @Override
      public double nextDouble() {
        return 0.5;
      }
    };
  }
}
