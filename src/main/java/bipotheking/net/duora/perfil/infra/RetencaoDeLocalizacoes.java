package bipotheking.net.duora.perfil.infra;

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
 * A célula some 90 dias depois da última atualização ({@code atualizada_em}): quem não usa o Duora
 * por esse tempo perde a localização e precisa ativá-la de novo. O raio dessas contas volta para
 * {@code SEM_LIMITE} (versão +1) na mesma instrução.
 */
@Component
class RetencaoDeLocalizacoes implements PoliticaDeRetencao {

  private static final Duration PRAZO = Duration.parse("P90D");

  private final JdbcClient jdbc;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  RetencaoDeLocalizacoes(JdbcClient jdbc, TransactionTemplate transacao, Clock relogio) {
    this.jdbc = jdbc;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  @Override
  public String nome() {
    return "localizacoes-paradas";
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

  /**
   * Apaga por {@code conta_id}, a chave que a gravação do raio trava com {@code for key share}, e
   * volta o raio das contas apagadas; devolve quantas localizações saíram.
   */
  private int apagarLote(OffsetDateTime antesDe) {
    return jdbc.sql(
            """
            with apagadas as (
                delete from localizacao where conta_id in (
                    select conta_id from localizacao where atualizada_em < :limite limit 1000)
                returning conta_id),
            reajustadas as (
                update preferencias set raio = 'SEM_LIMITE', raio_obrigatorio = false,
                    versao = versao + 1, atualizado_em = :agora
                where conta_id in (select conta_id from apagadas) and raio <> 'SEM_LIMITE'
                returning 1)
            select count(*) from apagadas
            """)
        .param("limite", antesDe)
        .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
        .query(Integer.class)
        .single();
  }

  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from localizacao where atualizada_em < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
