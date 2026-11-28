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
 * rotas e as dos {@link #PREFIXOS} com {@code no-cache}, como o index.html.
 */
@Configuration
public class RotasDoSpa implements WebMvcConfigurer {

  /**
   * A prova de vida: o {@link CabecalhosDeSeguranca} serve só este caminho, exato, com a CSP e a
   * Permissions-Policy da verificação. Espelha {@code CAMINHO_DA_VERIFICACAO} de {@code
   * caminhosDaCasca.ts}.
   */
  public static final String CAMINHO_DA_VERIFICACAO = "/verificacao";

  public static final List<String> CAMINHOS =
      List.of(
          "/entrar",
          "/entrar/confirmar",
          "/cadastro",
          CAMINHO_DA_VERIFICACAO,
          "/conta",
          "/termos",
          "/privacidade",
          "/denunciar",
          "/encarregado",
          "/perfil",
          "/perfil/preferencias",
          "/perfil/namoro",
          "/perfil/orientacao");

  /**
   * Prefixos de rotas com um segmento variável depois (o id da conta em {@code /perfis/{contaId}}).
   * Cada um é registrado com um único segmento a mais: {@code /perfis/} sozinho e {@code
   * /perfis/a/b} continuam 404. Espelha {@code PREFIXOS_DA_CASCA} de {@code caminhosDaCasca.ts}.
   */
  public static final List<String> PREFIXOS = List.of("/perfis/");

  private static final String INDEX = "forward:/index.html";

  @Override
  public void addViewControllers(ViewControllerRegistry registro) {
    CAMINHOS.forEach(caminho -> registro.addViewController(caminho).setViewName(INDEX));
    PREFIXOS.forEach(
        prefixo -> registro.addViewController(prefixo + "{contaId}").setViewName(INDEX));
  }
}
