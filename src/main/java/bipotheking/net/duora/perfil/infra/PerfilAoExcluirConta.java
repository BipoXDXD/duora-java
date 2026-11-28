package bipotheking.net.duora.perfil.infra;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Apaga o perfil na exclusão da conta. A conta fica como lápide (a linha não sai), então o {@code
 * on delete cascade} da chave estrangeira nunca dispara sozinho.
 */
@Component
class PerfilAoExcluirConta implements AoExcluirConta {

  private final JdbcClient jdbc;

  PerfilAoExcluirConta(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void excluir(ContaId conta) {
    jdbc.sql("delete from perfil where conta_id = :conta").param("conta", conta.valor()).update();
  }
}
