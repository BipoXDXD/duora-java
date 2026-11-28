package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Apaga a localização na exclusão da conta. A conta fica como lápide (a linha não sai), então o
 * {@code on delete cascade} da chave estrangeira nunca dispara sozinho.
 */
@Component
class LocalizacaoAoExcluirConta implements AoExcluirConta {

  private final JdbcClient jdbc;

  LocalizacaoAoExcluirConta(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void excluir(ContaId conta) {
    jdbc.sql("delete from localizacao where conta_id = :c").param("c", conta.valor()).update();
  }
}
