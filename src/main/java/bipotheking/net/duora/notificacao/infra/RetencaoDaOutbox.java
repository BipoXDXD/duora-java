package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * As duas políticas de retenção da {@code outbox}: a mensagem enviada some 7 dias depois de {@code
 * enviada_em}, a que falhou de vez (inclusive a de titular esquecido) 30 dias depois de {@code
 * falhou_em}. A pendente nunca sai por aqui: as duas colunas dela são nulas.
 */
@Configuration(proxyBeanMethods = false)
class RetencaoDaOutbox {

  @Bean
  PoliticaDeRetencao retencaoDaOutboxEnviada(JdbcClient jdbc, TransactionTemplate transacao) {
    return new PorInstante("outbox-enviada", Duration.parse("P7D"), "enviada_em", jdbc, transacao);
  }

  @Bean
  PoliticaDeRetencao retencaoDaOutboxFalhada(JdbcClient jdbc, TransactionTemplate transacao) {
    return new PorInstante("outbox-falhada", Duration.parse("P30D"), "falhou_em", jdbc, transacao);
  }

  /** Apaga as mensagens cujo instante terminal ({@code coluna}) é anterior ao limite. */
  private static final class PorInstante implements PoliticaDeRetencao {

    private static final int LOTE = 1000;

    private final String nome;
    private final Duration prazo;
    private final String apagarLote;
    private final String contar;
    private final JdbcClient jdbc;
    private final TransactionTemplate transacao;

    /** {@code coluna} é uma constante desta classe, nunca entrada externa. */
    PorInstante(
        String nome,
        Duration prazo,
        String coluna,
        JdbcClient jdbc,
        TransactionTemplate transacao) {
      this.nome = nome;
      this.prazo = prazo;
      this.apagarLote =
          """
          delete from outbox where id in (
              select id from outbox where %1$s < :limite limit %2$d)
          """
              .formatted(coluna, LOTE);
      this.contar = "select count(*) from outbox where %s is not null".formatted(coluna);
      this.jdbc = jdbc;
      this.transacao = transacao;
    }

    @Override
    public String nome() {
      return nome;
    }

    @Override
    public Duration prazo() {
      return prazo;
    }

    @Override
    public int expirar(Instant limite) {
      OffsetDateTime antesDe = OffsetDateTime.ofInstant(limite, ZoneOffset.UTC);
      return Lotes.apagarAteAcabar(() ->
          Objects.requireNonNull(
              transacao.execute(status -> jdbc.sql(apagarLote).param("limite", antesDe).update())));
    }

    @Override
    public long tamanho() {
      return jdbc.sql(contar).query(Long.class).single();
    }
  }
}
