package bipotheking.net.duora.room;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Os arquivos de /app/ levam o hash do conteúdo no nome: um conteúdo novo é uma URL nova, então
 * podem ficar em cache para sempre. O handler só põe o cabeçalho quando acha o arquivo; um 404 sai
 * sem ele, senão o navegador (e um CDN) guardaria a falha por um ano.
 */
@Configuration(proxyBeanMethods = false)
class BundlesComHash implements WebMvcConfigurer {

  private static final Duration UM_ANO = Duration.ofDays(365);

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/app/**")
        .addResourceLocations("classpath:/static/app/")
        .setCacheControl(CacheControl.maxAge(UM_ANO).cachePublic().immutable());
  }
}
