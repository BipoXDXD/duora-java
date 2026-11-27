package bipotheking.net.duora.conta.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Liga a negação por padrão a toda a API e o {@code @ContaAtual} aos handlers. */
@Configuration
class ContaWebConfig implements WebMvcConfigurer {

  private final ExigeSessao exigeSessao;

  ContaWebConfig(ExigeSessao exigeSessao) {
    this.exigeSessao = exigeSessao;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registro) {
    registro.addInterceptor(exigeSessao).addPathPatterns("/api/**");
  }

  @Override
  public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
    resolvers.add(new ContaAtualResolver());
  }
}
