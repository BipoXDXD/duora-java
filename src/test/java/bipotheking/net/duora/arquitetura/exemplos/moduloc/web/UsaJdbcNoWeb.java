package bipotheking.net.duora.arquitetura.exemplos.moduloc.web;

import org.springframework.jdbc.core.JdbcTemplate;

/** Viola a camada web: controller, filtro ou cookie não falam com o banco. */
class UsaJdbcNoWeb {

  private final JdbcTemplate jdbc;

  UsaJdbcNoWeb(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  int contar() {
    Integer total = jdbc.queryForObject("select 1", Integer.class);
    return total == null ? 0 : total;
  }
}
