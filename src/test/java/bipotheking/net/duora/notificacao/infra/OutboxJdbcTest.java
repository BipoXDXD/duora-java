package bipotheking.net.duora.notificacao.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.notificacao.infra.ProcessadorFalso.CargaFalsa;
import bipotheking.net.duora.nucleo.Correlacao;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxJdbcTest extends BancoDeTeste {

  private static final CargaFalsa CARGA = new CargaFalsa("ana@exemplo.invalid", "oi");

  @Autowired private Outbox outbox;
  @Autowired private TransactionTemplate transacao;

  @AfterEach
  void limparMdc() {
    MDC.remove(Correlacao.CHAVE_MDC);
  }

  private void registrar(String titular) {
    transacao.executeWithoutResult(status -> outbox.registrar(Outbox.EMAIL, titular, CARGA));
  }

  private long linhas() {
    return jdbc.sql("select count(*) from outbox").query(Long.class).single();
  }

  @Test
  void registrarSemTransacaoFalha() {
    assertThatThrownBy(() -> outbox.registrar(Outbox.EMAIL, "titular-a", CARGA))
        .isInstanceOf(IllegalTransactionStateException.class);
    assertThat(linhas()).isZero();
  }

  @Test
  void rollbackDescartaAMensagem() {
    transacao.executeWithoutResult(status -> {
      outbox.registrar(Outbox.EMAIL, "titular-a", CARGA);
      status.setRollbackOnly();
    });

    assertThat(linhas()).isZero();
  }

  @Test
  void gravaCorrelationIdDoMdc() {
    MDC.put(Correlacao.CHAVE_MDC, "req-1234-abcd");

    registrar("titular-a");

    assertThat(jdbc.sql("select correlation_id from outbox").query(String.class).single())
        .isEqualTo("req-1234-abcd");
  }

  @Test
  void registraPendenteComCargaParaJa() {
    registrar("titular-a");

    var linha =
        jdbc.sql("select tipo, carga ->> 'para' as para, tentativas from outbox")
            .query()
            .singleRow();
    assertThat(linha)
        .containsEntry("tipo", Outbox.EMAIL)
        .containsEntry("para", "ana@exemplo.invalid")
        .containsEntry("tentativas", 0);
    assertThat(
            jdbc.sql("select proxima_tentativa_em from outbox")
                .query(OffsetDateTime.class)
                .single()
                .toInstant())
        .isEqualTo(INICIO);
  }

  @Test
  void esquecerTitularZeraSoAsPendentesDele() {
    registrar("titular-a");
    registrar("titular-a");
    registrar("titular-b");
    jdbc.sql(
            """
            update outbox set carga = null, enviada_em = :agora
            where id = (select id from outbox where titular = 'titular-a' order by id limit 1)
            """)
        .param("agora", OffsetDateTime.parse("2026-11-22T11:00:00Z"))
        .update();
    relogio.avancar(Duration.ofMinutes(1));

    int esquecidas = outbox.esquecerTitular("titular-a");

    assertThat(esquecidas).isEqualTo(1);
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from outbox where titular = 'titular-a' and carga is null
                      and falhou_em = :agora and ultimo_erro = 'titular-esquecido'
                    """)
                .param("agora", OffsetDateTime.parse("2026-11-22T12:01:00Z"))
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    """
                    select count(*) from outbox where titular = 'titular-a'
                      and enviada_em is not null and falhou_em is null and ultimo_erro is null
                    """)
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql(
                    "select count(*) from outbox where titular = 'titular-b' and carga is not null")
                .query(Long.class)
                .single())
        .isEqualTo(1);
  }
}
