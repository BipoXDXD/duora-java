package bipotheking.net.duora;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
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
  static final ArchRule semCiclosEntreModulos = RegrasDeArquitetura.semCiclosEntreModulos(RAIZ);

  @ArchTest
  static final ArchRule moduloSoUsaAApiPublicaDeOutro = RegrasDeArquitetura.apiPublica(RAIZ);

  @ArchTest
  static final ArchRule dominioSemWebNemJdbc = RegrasDeArquitetura.dominioSemWebNemJdbc(RAIZ);

  @ArchTest static final ArchRule webSemJdbc = RegrasDeArquitetura.webSemJdbc(RAIZ);

  @ArchTest
  static final ArchRule dominioJavaPuro =
      RegrasDeArquitetura.dominioJavaPuro(RAIZ).allowEmptyShould(false);

  /** Mais forte que "sem Spring": o núcleo só usa o JDK (e anotações de nulidade). */
  @ArchTest static final ArchRule nucleoSemSpring = RegrasDeArquitetura.nucleoJavaPuro(RAIZ);

  /** ADR 0006: {@code retencao} é só a raiz; web e JDBC já ficam de fora pela regra do domínio. */
  @ArchTest
  static final ArchRule retencaoSoNaRaiz =
      classes()
          .that()
          .resideInAPackage(RAIZ + ".retencao..")
          .should()
          .resideInAPackage(RAIZ + ".retencao")
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
