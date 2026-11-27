package bipotheking.net.duora.arquitetura.exemplos.moduloc.dominio;

import org.springframework.jdbc.core.JdbcTemplate;

/** Viola o domínio puro: usa JDBC fora de {@code infra}. */
class UsaJdbc {

  private final JdbcTemplate jdbc;

  UsaJdbc(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  int contar() {
    Integer total = jdbc.queryForObject("select 1", Integer.class);
    return total == null ? 0 : total;
  }
}
