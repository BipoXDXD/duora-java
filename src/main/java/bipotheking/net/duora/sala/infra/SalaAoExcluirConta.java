package bipotheking.net.duora.sala.infra;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Apaga o que {@code sala} guarda da conta na exclusão. A conta fica como lápide (a linha não sai),
 * então o {@code on delete cascade} das chaves estrangeiras nunca dispara sozinho.
 */
@Component
class SalaAoExcluirConta implements AoExcluirConta {

  private final JdbcClient jdbc;

  SalaAoExcluirConta(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void excluir(ContaId conta) {
    jdbc.sql("delete from fila_espera where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
    jdbc.sql("delete from cenario_jogado where conta_id = :conta")
        .param("conta", conta.valor())
        .update();
  }
}
