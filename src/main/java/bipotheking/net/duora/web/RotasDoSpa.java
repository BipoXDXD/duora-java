package bipotheking.net.duora.web;

import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rotas da casca de conta abertas direto no navegador (link de e-mail, recarga, favorito): o Spring
 * encaminha cada uma ao index.html, e o despacho do front decide quem monta. Espelha {@code
 * frontend/src/rotas/caminhosDaCasca.ts} ({@code RotasDoSpaTest} trava a igualdade). Nenhuma começa
 * por /app/, reservado aos bundles com cache imutável; o {@link CabecalhosDeSeguranca} serve estas
 * rotas com {@code no-cache}, como o index.html.
 */
@Configuration
public class RotasDoSpa implements WebMvcConfigurer {

  public static final List<String> CAMINHOS =
      List.of(
          "/entrar",
          "/entrar/confirmar",
          "/cadastro",
          "/aguardando",
          "/conta",
          "/termos",
          "/privacidade",
          "/denunciar",
          "/encarregado");

  /** Prova de vida: fora de CAMINHOS até a F3 movê-la com o front (e tirar /aguardando). */
  public static final String VERIFICACAO = "/verificacao";

  private static final String INDEX = "forward:/index.html";

  @Override
  public void addViewControllers(ViewControllerRegistry registro) {
    CAMINHOS.forEach(caminho -> registro.addViewController(caminho).setViewName(INDEX));
    registro.addViewController(VERIFICACAO).setViewName(INDEX);
  }
}
