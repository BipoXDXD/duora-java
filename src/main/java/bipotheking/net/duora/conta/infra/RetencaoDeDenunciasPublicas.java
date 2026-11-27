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
 * A denúncia pública resolvida some 183 dias depois da {@code criada_em} (o caso fechado mais seis
 * meses). A pendente fica até a moderação resolvê-la.
 */
@Component
class RetencaoDeDenunciasPublicas implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P183D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeDenunciasPublicas(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "denuncias-publicas";
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
            delete from denuncia_publica where id in (
                select id from denuncia_publica
                where situacao = 'RESOLVIDA' and criada_em < :limite limit 1000)
            """)
        .param("limite", antesDe)
        .update();
  }

  /** As resolvidas que já passaram do prazo e esperam a próxima execução. */
  @Override
  public long tamanho() {
    return jdbc.sql(
            """
            select count(*) from denuncia_publica
            where situacao = 'RESOLVIDA' and criada_em < :limite
            """)
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
