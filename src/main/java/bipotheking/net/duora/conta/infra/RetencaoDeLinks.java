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
 * O link de entrada some um dia depois de {@code expira_em}, usado ou não. A recusa por idade e a
 * exclusão de conta já apagam os links do e-mail na hora; esta política leva o resto, que guarda o
 * e-mail em texto puro.
 */
@Component
class RetencaoDeLinks implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P1D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeLinks(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "links-de-entrada";
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
            delete from link_de_entrada where id in (
                select id from link_de_entrada where expira_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  /** Os links vencidos há mais de um dia: os que a próxima rodada apaga. */
  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from link_de_entrada where expira_em < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
