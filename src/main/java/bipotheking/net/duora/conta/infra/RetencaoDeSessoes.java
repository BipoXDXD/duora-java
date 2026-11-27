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
 * A sessão some assim que {@code expira_em} passa: prazo zero, porque o {@code expira_em} já é o
 * fim da vida dela (a renovação deslizante o empurra para frente enquanto ela é usada).
 */
@Component
class RetencaoDeSessoes implements PoliticaDeRetencao {

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeSessoes(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "sessoes-expiradas";
  }

  @Override
  public Duration prazo() {
    return Duration.ZERO;
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
            delete from sessao where id in (
                select id from sessao where expira_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from sessao where expira_em < :agora")
        .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
