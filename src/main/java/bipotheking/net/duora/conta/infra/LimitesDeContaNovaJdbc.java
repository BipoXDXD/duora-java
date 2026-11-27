package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.TipoDeLimiteNovo;
import bipotheking.net.duora.conta.dominio.RepositorioDeUsoDeContaNova;
import java.time.LocalDate;
import java.util.OptionalInt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Uso de conta nova no Postgres. Um só upsert condicional, nunca ler e depois gravar: o primeiro
 * consumo do dia insere; os seguintes travam a linha e o {@code where} é reavaliado na versão que a
 * trava liberou, então consumos simultâneos nunca passam do limite.
 */
@Component
class LimitesDeContaNovaJdbc implements RepositorioDeUsoDeContaNova {

  private final JdbcClient jdbc;

  LimitesDeContaNovaJdbc(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public OptionalInt consumir(ContaId conta, LocalDate dia, TipoDeLimiteNovo tipo, int limite) {
    // A inserção grava 1 sem olhar o limite: com limite 0, o primeiro consumo do dia passaria.
    if (limite < 1) {
      throw new IllegalArgumentException("O limite precisa ser pelo menos 1: " + limite);
    }
    return jdbc.sql(
            """
            insert into uso_conta_nova (conta_id, dia, tipo, usados)
            values (:conta, :dia, :tipo, 1)
            on conflict (conta_id, dia, tipo) do update
                set usados = uso_conta_nova.usados + 1
                where uso_conta_nova.usados < :limite
            returning usados
            """)
        .param("conta", conta.valor())
        .param("dia", dia)
        .param("tipo", tipo.name())
        .param("limite", limite)
        .query(Integer.class)
        .optional()
        .map(OptionalInt::of)
        .orElseGet(OptionalInt::empty);
  }
}
