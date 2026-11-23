package bipotheking.net.duora.web;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.stereotype.Component;

/**
 * Serve o {@code .webmanifest} como {@code application/manifest+json}: a tabela padrão do Spring
 * Boot não conhece a extensão, e o Tomcat a mandaria sem tipo.
 */
@Component
class TipoDoManifest implements WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> {

  @Override
  public void customize(ConfigurableServletWebServerFactory fabrica) {
    MimeMappings tipos = new MimeMappings();
    tipos.add("webmanifest", "application/manifest+json");
    fabrica.addMimeMappings(tipos);
  }
}
