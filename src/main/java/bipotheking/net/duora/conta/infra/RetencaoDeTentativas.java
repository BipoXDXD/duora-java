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
 * A tentativa de verificação de idade some um dia depois de {@code criada_em}, concluída ou não:
 * ela só serve ao limite das últimas 24 h e à faixa de dúvida. A aprovação fica em {@code
 * verificacao_idade}, fora desta política.
 */
@Component
class RetencaoDeTentativas implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P1D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeTentativas(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "tentativas-de-verificacao";
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
            delete from tentativa_verificacao where id in (
                select id from tentativa_verificacao where criada_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  /** As tentativas criadas há mais de um dia: as que a próxima rodada apaga. */
  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from tentativa_verificacao where criada_em < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
