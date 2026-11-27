package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O uso de conta nova some 8 dias depois do fim do {@code dia} (meia-noite de São Paulo); o limite
 * só olha o dia corrente. A exclusão da conta não o apaga: a conta vira lápide, o cascade não
 * dispara, e o contador sem e-mail sai por aqui.
 */
@Component
class RetencaoDeUsoDeContaNova implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P8D");
  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeUsoDeContaNova(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "uso-conta-nova";
  }

  @Override
  public Duration prazo() {
    return PRAZO;
  }

  /** Apaga os dias que acabaram até {@code limite}: os anteriores ao dia dele em São Paulo. */
  @Override
  public int expirar(Instant limite) {
    LocalDate antesDe = LocalDate.ofInstant(limite, FUSO);
    return Lotes.apagarAteAcabar(
        () -> Objects.requireNonNull(transacao.execute(status -> apagarLote(antesDe))));
  }

  private int apagarLote(LocalDate antesDe) {
    return jdbc.sql(
            """
            delete from uso_conta_nova where (conta_id, dia, tipo) in (
                select conta_id, dia, tipo from uso_conta_nova where dia < :dia limit 1000)
            """)
        .param("dia", antesDe)
        .update();
  }

  /** Os dias que já passaram do prazo e esperam a próxima execução. */
  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from uso_conta_nova where dia < :dia")
        .param("dia", LocalDate.ofInstant(relogio.instant().minus(PRAZO), FUSO))
        .query(Long.class)
        .single();
  }
}
