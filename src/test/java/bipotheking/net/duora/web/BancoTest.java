package bipotheking.net.duora.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BancoTest extends BancoDeTeste {

  @Autowired private DataSource dataSource;

  @Test
  void versaoEPostgres18() {
    String versao = jdbc.sql("select version()").query(String.class).single();

    assertThat(versao).startsWith("PostgreSQL 18");
  }

  @Test
  void flywayAplicouV1() {
    Boolean sucesso =
        jdbc.sql("select success from flyway_schema_history where version = '1'")
            .query(Boolean.class)
            .single();

    assertThat(sucesso).isTrue();
  }

  @Test
  void healthResponde() throws Exception {
    mvc.perform(get("/actuator/health")).andExpect(status().isOk());
  }

  @Test
  void envNaoExposto() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
  }

  @Test
  void configDeTesteCarregada() throws SQLException {
    assertThat(dataSource.unwrap(HikariDataSource.class).getMaximumPoolSize()).isEqualTo(4);
  }

  @Test
  void transacaoOciosaExpira() {
    String limite =
        jdbc.sql("show idle_in_transaction_session_timeout").query(String.class).single();

    assertThat(limite).isEqualTo("30s");
  }

  @Test
  void limparEsvaziaTabelasMenosHistorico() {
    jdbc.sql("create table tabela_teste (id int primary key)").update();
    try {
      jdbc.sql("insert into tabela_teste values (1)").update();
      long historicoAntes = contar("flyway_schema_history");

      limpar();

      assertThat(contar("tabela_teste")).isZero();
      assertThat(contar("flyway_schema_history")).isEqualTo(historicoAntes).isPositive();
    } finally {
      jdbc.sql("drop table tabela_teste").update();
    }
  }

  private long contar(String tabela) {
    return jdbc.sql("select count(*) from " + tabela).query(Long.class).single();
  }
}
