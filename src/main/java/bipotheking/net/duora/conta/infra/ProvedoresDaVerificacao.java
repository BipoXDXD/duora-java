package bipotheking.net.duora.conta.infra;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * A config base não escolhe os provedores da verificação: prod liga os reais, e desenvolvimento,
 * testes e e2e ligam os falsos (perfil {@code verificacao-falsa} ou a config de teste). Sem
 * escolha, nenhum adapter seria registrado e o boot cairia num "bean não encontrado" que não diz o
 * que falta; esta checagem roda antes de qualquer bean e diz a chave e os valores aceitos. Os
 * valores seguem a condição que liga os adapters, que ignora maiúsculas.
 */
@Component
class ProvedoresDaVerificacao implements BeanFactoryPostProcessor, EnvironmentAware {

  private static final String PROVA_DE_VIDA = "duora.verificacao.provedor";
  private static final String DOCUMENTO = "duora.verificacao.documento.provedor";

  private @Nullable Environment ambiente;

  @Override
  public void setEnvironment(Environment ambiente) {
    this.ambiente = ambiente;
  }

  @Override
  public void postProcessBeanFactory(ConfigurableListableBeanFactory beans) {
    Environment configurado = Objects.requireNonNull(ambiente, "Environment não injetado");
    exigir(configurado, PROVA_DE_VIDA, List.of("aws", "falso"));
    exigir(configurado, DOCUMENTO, List.of("didit", "falso"));
  }

  private static void exigir(Environment ambiente, String chave, List<String> aceitos) {
    String valor = ambiente.getProperty(chave);
    String opcoes = String.join(" ou ", aceitos);
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException(
          chave
              + " sem valor; use "
              + opcoes
              + " (no desenvolvimento, o perfil verificacao-falsa liga os falsos)");
    }
    if (!aceitos.contains(valor.toLowerCase(Locale.ROOT))) {
      throw new IllegalStateException(chave + "=" + valor + " não existe; use " + opcoes);
    }
  }
}
