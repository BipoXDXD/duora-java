package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O registro de acesso some 183 dias depois do {@code instante}, o prazo do Marco Civil (art. 15).
 * A exclusão da conta não o apaga: a conta vira lápide e o registro fica até o fim do prazo.
 */
@Component
class RetencaoDeAcessos implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P183D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeAcessos(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "registros-de-acesso";
  }

  @Override
  public Duration prazo() {
    return PRAZO;
  }

  @Override
  public int expirar(Instant limite) {
    OffsetDateTime antesDe = OffsetDateTime.ofInstant(limite, ZoneOffset.UTC);
    return Lotes.apagarAteAcabar(
        () -> Objects.requireNonNull(transacao.execute(status -> apagarLote(antesDe))));
  }

  private int apagarLote(OffsetDateTime antesDe) {
    return jdbc.sql(
            """
            delete from registro_acesso where id in (
                select id from registro_acesso where instante < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  /** Os registros que já passaram do prazo e esperam a próxima execução. */
  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from registro_acesso where instante < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
