package bipotheking.net.duora;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.arquitetura.RegrasDeArquitetura;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fitness functions das fronteiras dos módulos, sobre as classes de produção, no {@code verify}.
 */
@AnalyzeClasses(
    packages = ArquiteturaTest.RAIZ,
    importOptions = ImportOption.DoNotIncludeTests.class)
class ArquiteturaTest {

  static final String RAIZ = "bipotheking.net.duora";

  @ArchTest
  static final ArchRule semCiclosEntreModulos =
      slices().matching(RAIZ + ".(*)..").should().beFreeOfCycles().allowEmptyShould(false);

  @ArchTest
  static final ArchRule moduloSoUsaAApiPublicaDeOutro = RegrasDeArquitetura.apiPublica(RAIZ);

  @ArchTest
  static final ArchRule dominioSemWebNemJdbc = RegrasDeArquitetura.dominioSemWebNemJdbc(RAIZ);

  @ArchTest
  static final ArchRule nucleoSemSpring =
      noClasses()
          .that()
          .resideInAPackage(RAIZ + ".nucleo..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("org.springframework..")
          .allowEmptyShould(false);

  /** Garante que as regras acima olham bytecode de verdade (Java 25), não um import vazio. */
  @ArchTest
  static void importaAsClassesDeProducao(JavaClasses classes) {
    assertThat(classes.contain(DuoraApplication.class)).isTrue();
  }

  @ArchTest
  static void deixaAsClassesDeTesteDeFora(JavaClasses classes) {
    assertThat(classes.containPackage(RAIZ + ".arquitetura")).isFalse();
  }
}
