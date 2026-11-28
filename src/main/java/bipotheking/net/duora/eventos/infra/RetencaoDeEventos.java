package bipotheking.net.duora.eventos.infra;

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
 * O evento de tempo real vive 1 dia depois de {@code criado_em}: o stream só retoma o que perdeu
 * numa reconexão curta, e o resto volta pelo estado inicial.
 */
@Component
class RetencaoDeEventos implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P1D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeEventos(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "eventos-de-conta";
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
            delete from evento_conta where seq in (
                select seq from evento_conta where criado_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from evento_conta where criado_em < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
