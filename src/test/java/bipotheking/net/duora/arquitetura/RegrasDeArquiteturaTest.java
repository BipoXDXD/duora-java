package bipotheking.net.duora.arquitetura;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Prova que as regras mordem: cada exemplo errado em {@code exemplos} gera uma violação. */
class RegrasDeArquiteturaTest {

  private static final String RAIZ = "bipotheking.net.duora.arquitetura.exemplos";

  private final JavaClasses exemplos = new ClassFileImporter().importPackages(RAIZ);

  @Test
  void apiPublicaMorde() {
    assertThat(violacoes(RegrasDeArquitetura.apiPublica(RAIZ)))
        .isNotEmpty()
        .allSatisfy(v -> assertThat(v).contains("moduloa.UsaInterno", "modulob.infra.Interno"));
  }

  @Test
  void dominioSemJdbcMorde() {
    assertThat(violacoes(RegrasDeArquitetura.dominioSemWebNemJdbc(RAIZ)))
        .anySatisfy(v -> assertThat(v).contains("UsaJdbc").contains("JdbcTemplate"));
  }

  @Test
  void dominioSemHttpMorde() {
    assertThat(violacoes(RegrasDeArquitetura.dominioSemWebNemJdbc(RAIZ)))
        .anySatisfy(v -> assertThat(v).contains("UsaHttp").contains("HttpStatus"));
  }

  @Test
  void webSemJdbcMorde() {
    assertThat(violacoes(RegrasDeArquitetura.webSemJdbc(RAIZ)))
        .isNotEmpty()
        .allSatisfy(v -> assertThat(v).contains("UsaJdbcNoWeb", "JdbcTemplate"));
  }

  @Test
  void dominioJavaPuroMorde() {
    assertThat(violacoes(RegrasDeArquitetura.dominioJavaPuro(RAIZ)))
        .anySatisfy(v -> assertThat(v).contains("UsaSpring").contains("StringUtils"));
  }

  @Test
  void dominioJavaPuroMordeDependenciaDaInfra() {
    assertThat(violacoes(RegrasDeArquitetura.dominioJavaPuro(RAIZ)))
        .anySatisfy(v -> assertThat(v).contains("UsaInfra").contains("infra.Repositorio"));
  }

  @Test
  void dominioJavaPuroMordeDependenciaDaWeb() {
    assertThat(violacoes(RegrasDeArquitetura.dominioJavaPuro(RAIZ)))
        .anySatisfy(v -> assertThat(v).contains("UsaWeb").contains("web.Controlador"));
  }

  @Test
  void semCiclosEntreModulosMorde() {
    assertThat(violacoes(RegrasDeArquitetura.semCiclosEntreModulos(RAIZ)))
        .isNotEmpty()
        .allSatisfy(v -> assertThat(v).contains("modulod.Ida", "moduloe.Volta"));
  }

  private List<String> violacoes(ArchRule regra) {
    return regra.evaluate(exemplos).getFailureReport().getDetails();
  }
}
