package bipotheking.net.duora.web;

import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A chave de idempotência vive 24 horas depois de {@code criada_em}, com ou sem resposta gravada:
 * uma repetição depois disso é um pedido novo. Mora em {@code web/} ao lado da {@link
 * Idempotencia}, que é dona da tabela.
 */
@Component
class RetencaoDeIdempotencia implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P1D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;

  RetencaoDeIdempotencia(JdbcClient jdbc, TransactionTemplate transacao) {
    this.jdbc = jdbc;
    this.transacao = transacao;
  }

  @Override
  public String nome() {
    return "chaves-de-idempotencia";
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
            delete from chave_de_idempotencia where (escopo, chave) in (
                select escopo, chave from chave_de_idempotencia
                where criada_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from chave_de_idempotencia").query(Long.class).single();
  }
}
