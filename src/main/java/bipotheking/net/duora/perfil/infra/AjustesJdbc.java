package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Raio;
import bipotheking.net.duora.perfil.dominio.AjustesDeBusca;
import bipotheking.net.duora.perfil.dominio.AjustesVersionados;
import bipotheking.net.duora.perfil.dominio.RepositorioDeAjustes;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A tabela {@code preferencias} no Postgres. O raio diferente de {@code SEM_LIMITE} exige a linha
 * da localização, conferida com {@code for key share}: o {@code DELETE} de uma revogação ou da
 * retenção espera esta transação terminar e, depois, volta o raio sobre a linha nova; se o {@code
 * DELETE} veio antes, a linha some e a gravação não acontece.
 */
@Component
class AjustesJdbc implements RepositorioDeAjustes {

  private static final String LOCALIZACAO_ATIVA_SE_PRECISA =
      """
      (cast(:raio as text) = 'SEM_LIMITE'
          or exists (select 1 from localizacao where conta_id = :conta for key share))
      """;

  private final JdbcClient jdbc;

  AjustesJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<AjustesVersionados> buscar(ContaId conta) {
    return jdbc.sql(
            """
            select idade_minima, idade_maxima, raio, raio_obrigatorio, versao
            from preferencias where conta_id = :conta
            """)
        .param("conta", conta.valor())
        .query((rs, linha) -> versionados(rs))
        .optional();
  }

  @Override
  public boolean criar(ContaId conta, AjustesDeBusca ajustes, Instant agora) {
    return jdbc.sql(
                """
                insert into preferencias (conta_id, idade_minima, idade_maxima, raio,
                    raio_obrigatorio, versao, atualizado_em)
                select :conta, :minima, :maxima, :raio, :obrigatorio, 1, :agora
                where %s
                on conflict (conta_id) do nothing
                """
                    .formatted(LOCALIZACAO_ATIVA_SE_PRECISA))
            .param("conta", conta.valor())
            .param("minima", ajustes.faixa().minima())
            .param("maxima", ajustes.faixa().maxima())
            .param("raio", ajustes.raio().name())
            .param("obrigatorio", ajustes.raioObrigatorio())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .update()
        == 1;
  }

  @Override
  public Optional<AjustesVersionados> atualizar(
      ContaId conta, long versao, AjustesDeBusca ajustes, Instant agora) {
    int linhas =
        jdbc.sql(
                """
                update preferencias set idade_minima = :minima, idade_maxima = :maxima,
                    raio = :raio, raio_obrigatorio = :obrigatorio, versao = versao + 1,
                    atualizado_em = :agora
                where conta_id = :conta and versao = :versao and %s
                """
                    .formatted(LOCALIZACAO_ATIVA_SE_PRECISA))
            .param("conta", conta.valor())
            .param("versao", versao)
            .param("minima", ajustes.faixa().minima())
            .param("maxima", ajustes.faixa().maxima())
            .param("raio", ajustes.raio().name())
            .param("obrigatorio", ajustes.raioObrigatorio())
            .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
            .update();
    if (linhas == 0) {
      return Optional.empty();
    }
    return Optional.of(new AjustesVersionados(ajustes, versao + 1));
  }

  @Override
  public void voltarParaSemLimite(ContaId conta, Instant agora) {
    jdbc.sql(
            """
            update preferencias set raio = 'SEM_LIMITE', raio_obrigatorio = false,
                versao = versao + 1, atualizado_em = :agora
            where conta_id = :conta and raio <> 'SEM_LIMITE'
            """)
        .param("conta", conta.valor())
        .param("agora", OffsetDateTime.ofInstant(agora, ZoneOffset.UTC))
        .update();
  }

  private static AjustesVersionados versionados(ResultSet rs) throws SQLException {
    AjustesDeBusca ajustes =
        new AjustesDeBusca(
            new FaixaEtaria(rs.getInt("idade_minima"), rs.getInt("idade_maxima")),
            Raio.valueOf(rs.getString("raio")),
            rs.getBoolean("raio_obrigatorio"));
    return new AjustesVersionados(ajustes, rs.getLong("versao"));
  }
}
