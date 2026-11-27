package bipotheking.net.duora.arquitetura;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Arrays;
import java.util.Optional;

/**
 * Regras de fronteira parametrizadas pela raiz dos módulos. O módulo de uma classe é o segmento de
 * pacote logo depois da raiz ({@code raiz.conta.dominio} pertence a {@code conta}).
 */
public final class RegrasDeArquitetura {

  private RegrasDeArquitetura() {}

  /** Nenhum ciclo de dependência entre módulos ({@code raiz.A} usa {@code raiz.B}, que usa A). */
  public static ArchRule semCiclosEntreModulos(String raiz) {
    return slices().matching(raiz + ".(*)..").should().beFreeOfCycles().allowEmptyShould(false);
  }

  /** Uma classe de {@code raiz.A..} só usa, de outro módulo {@code B}, o pacote {@code raiz.B}. */
  public static ArchRule apiPublica(String raiz) {
    return classes()
        .that()
        .resideInAPackage(raiz + ".*..")
        .should(new SoUsaApiPublicaDeOutroModulo(raiz))
        .allowEmptyShould(false);
  }

  /**
   * Raiz e {@code dominio/} de cada módulo não conhecem web, HTTP, JDBC, Spring Data nem Jackson.
   */
  public static ArchRule dominioSemWebNemJdbc(String raiz) {
    return noClasses()
        .that()
        .resideInAPackage(raiz + "..")
        .and()
        .resideOutsideOfPackages(
            "..web..",
            "..infra..",
            // room/ é o terraço anônimo legado, ainda sem camadas; sai desta exceção na Etapa 5.
            raiz + ".room..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework.web..",
            "org.springframework.http..",
            "org.springframework.jdbc..",
            "org.springframework.data..",
            "jakarta.servlet..",
            "tools.jackson..",
            "com.fasterxml.jackson..")
        .allowEmptyShould(false);
  }

  /**
   * A camada web não fala com o banco. Exceções registradas nas Global Constraints: a reserva e a
   * retenção das chaves de idempotência, que vivem em {@code web/}.
   */
  public static ArchRule webSemJdbc(String raiz) {
    return noClasses()
        .that()
        .resideInAPackage(raiz + "..web..")
        .and(
            DescribedPredicate.not(
                umaDas(raiz + ".web.Idempotencia", raiz + ".web.RetencaoDeIdempotencia")))
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.jdbc..", "org.springframework.data..")
        .allowEmptyShould(false);
  }

  /** O {@code dominio/} de cada módulo é Java puro: só JDK e o próprio projeto. */
  public static ArchRule dominioJavaPuro(String raiz) {
    return javaPuro(
        raiz + "..dominio..",
        resideInAPackage(raiz + "..")
            .and(DescribedPredicate.not(resideInAnyPackage("..infra..", "..web.."))));
  }

  /** O {@code nucleo} é Java puro: só JDK e ele mesmo. */
  public static ArchRule nucleoJavaPuro(String raiz) {
    return javaPuro(raiz + ".nucleo..", resideInAPackage(raiz + ".nucleo.."));
  }

  private static ArchRule javaPuro(String pacotes, DescribedPredicate<JavaClass> projetoPermitido) {
    return classes()
        .that()
        .resideInAPackage(pacotes)
        .should()
        .onlyDependOnClassesThat(
            // org.jspecify: anotações de nulidade, sem comportamento (o nucleo já usa @Nullable).
            resideInAnyPackage("java..", "org.jspecify..")
                .and(DescribedPredicate.not(resideInAPackage("java.sql..")))
                .or(projetoPermitido))
        .allowEmptyShould(false);
  }

  /** A classe ou uma aninhada nela ({@code Classe$Interna}). */
  private static DescribedPredicate<JavaClass> umaDas(String... nomes) {
    return DescribedPredicate.describe(
        "uma de " + String.join(", ", nomes),
        classe ->
            Arrays.stream(nomes)
                .anyMatch(n -> classe.getName().equals(n) || classe.getName().startsWith(n + "$")));
  }

  private static final class SoUsaApiPublicaDeOutroModulo extends ArchCondition<JavaClass> {

    private final String prefixo;

    SoUsaApiPublicaDeOutroModulo(String raiz) {
      super("depender de outro módulo só pelo pacote raiz dele (a API pública)");
      this.prefixo = raiz + ".";
    }

    @Override
    public void check(JavaClass classe, ConditionEvents eventos) {
      Optional<String> origem = modulo(classe.getPackageName());
      for (Dependency dependencia : classe.getDirectDependenciesFromSelf()) {
        String pacoteAlvo = dependencia.getTargetClass().getBaseComponentType().getPackageName();
        Optional<String> alvo = modulo(pacoteAlvo);
        boolean outroModulo = alvo.isPresent() && !alvo.equals(origem);
        if (outroModulo && !pacoteAlvo.equals(prefixo + alvo.get())) {
          eventos.add(SimpleConditionEvent.violated(dependencia, dependencia.getDescription()));
        }
      }
    }

    private Optional<String> modulo(String pacote) {
      if (!pacote.startsWith(prefixo)) {
        return Optional.empty();
      }
      String resto = pacote.substring(prefixo.length());
      int ponto = resto.indexOf('.');
      return Optional.of(ponto < 0 ? resto : resto.substring(0, ponto));
    }
  }
}
