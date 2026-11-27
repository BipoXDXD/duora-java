package bipotheking.net.duora.web;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base dos testes com banco: um contexto só, com o Postgres do Testcontainers da config de teste
 * ({@code classpath:/config/application.properties}) e o relógio controlável.
 *
 * <p>Convenção: quem estende não declara {@code properties=} nem {@code @TestConfiguration}
 * próprios, para todos dividirem o mesmo contexto (e o mesmo pool pequeno).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(RelogioDeTesteConfig.class)
public abstract class BancoDeTeste {

  public static final Instant INICIO = Instant.parse("2026-11-22T12:00:00Z");

  private static final String HISTORICO_DO_FLYWAY = "flyway_schema_history";

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected RelogioDeTeste relogio;
  @Autowired private LimiteDeTaxa limiteDeTaxa;

  /**
   * Esvazia todas as tabelas de {@code public}, menos o histórico do Flyway, volta o relógio e
   * enche de novo os baldes de limite de taxa (com o relógio de volta, eles nunca recarregariam
   * sozinhos).
   */
  @BeforeEach
  protected void limpar() {
    List<String> tabelas =
        jdbc.sql(
                """
                select quote_ident(tablename) from pg_tables
                where schemaname = 'public' and tablename <> :historico
                """)
            .param("historico", HISTORICO_DO_FLYWAY)
            .query(String.class)
            .list();
    if (!tabelas.isEmpty()) {
      jdbc.sql("truncate table " + String.join(", ", tabelas) + " cascade").update();
    }
    relogio.ajustar(INICIO);
    limiteDeTaxa.esquecerBaldes();
  }
}
