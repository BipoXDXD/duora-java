package bipotheking.net.duora.arquitetura;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.Optional;

/**
 * Regras de fronteira parametrizadas pela raiz dos módulos. O módulo de uma classe é o segmento de
 * pacote logo depois da raiz ({@code raiz.conta.dominio} pertence a {@code conta}).
 */
public final class RegrasDeArquitetura {

  private RegrasDeArquitetura() {}

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
