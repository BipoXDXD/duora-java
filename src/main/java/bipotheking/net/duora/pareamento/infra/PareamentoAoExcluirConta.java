package bipotheking.net.duora.pareamento.infra;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Apaga os vetos da conta, dos dois lados do par, na exclusão. A conta fica como lápide (a linha
 * não sai), então o {@code on delete cascade} das chaves estrangeiras nunca dispara sozinho.
 */
@Component
class PareamentoAoExcluirConta implements AoExcluirConta {

  private final JdbcClient jdbc;

  PareamentoAoExcluirConta(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void excluir(ContaId conta) {
    jdbc.sql("delete from par_vetado where conta_menor = :c or conta_maior = :c")
        .param("c", conta.valor())
        .update();
  }
}
