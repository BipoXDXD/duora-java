package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.retencao.Lotes;
import bipotheking.net.duora.retencao.PoliticaDeRetencao;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
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
   * volta o raio das contas apagadas; devolve quantas localizações saíram. São dois comandos: o
   * {@code UPDATE} tira um snapshot novo depois que o {@code DELETE} esperou uma gravação de raio
   * em curso, e assim pega a linha que ela acabou de gravar (numa CTE única, o snapshot seria o do
   * início da instrução e o raio novo escaparia).
   */
  private int apagarLote(OffsetDateTime antesDe) {
    List<UUID> apagadas =
        jdbc.sql(
                """
                delete from localizacao where conta_id in (
                    select conta_id from localizacao where atualizada_em < :limite limit 1000)
                returning conta_id
                """)
            .param("limite", antesDe)
            .query(UUID.class)
            .list();
    if (!apagadas.isEmpty()) {
      jdbc.sql(
              """
              update preferencias set raio = 'SEM_LIMITE', raio_obrigatorio = false,
                  versao = versao + 1, atualizado_em = :agora
              where conta_id in (:contas) and raio <> 'SEM_LIMITE'
              """)
          .param("contas", apagadas)
          .param("agora", OffsetDateTime.ofInstant(relogio.instant(), ZoneOffset.UTC))
          .update();
    }
    return apagadas.size();
  }

  @Override
  public long tamanho() {
    return jdbc.sql("select count(*) from localizacao where atualizada_em < :limite")
        .param("limite", OffsetDateTime.ofInstant(relogio.instant().minus(PRAZO), ZoneOffset.UTC))
        .query(Long.class)
        .single();
  }
}
