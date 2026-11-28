package bipotheking.net.duora.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Cabeçalhos de segurança em toda resposta. A API nunca é guardada em cache; o index.html, o
 * service worker e o manifest são revalidados a cada busca, para uma versão nova chegar no próximo
 * carregamento. O cache imutável dos bundles de /app/ fica em {@link BundlesComHash}, que só o põe
 * quando o arquivo existe.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class CabecalhosDeSeguranca extends OncePerRequestFilter {

  /**
   * O script e o iframe do Turnstile (challenges.cloudflare.com) são a única origem externa, como
   * pede a doc da Cloudflare; o widget não precisa de style-src nem de 'unsafe-*'.
   */
  public static final String CSP =
      "default-src 'self'; img-src 'self' data:; object-src 'none'; base-uri 'none'; "
          + "frame-ancestors 'none'; form-action 'self'; "
          + "script-src 'self' https://challenges.cloudflare.com; "
          + "frame-src https://challenges.cloudflare.com";

  /**
   * Política do documento da prova de vida, servida só quando o caminho pedido é exatamente {@link
   * RotasDoSpa#CAMINHO_DA_VERIFICACAO}. Parte da política validada no C# com três desvios: sai o
   * {@code wss} do hub (a 2b não tem tempo real), sai o hash de estilo do {@code <model-viewer>} (a
   * rota não tem terraço) e {@code trusted-types} vira {@code 'none'} (sem {@code lit-html}). O
   * {@code 'wasm-unsafe-eval'} compila o WASM do detector (sem eval de JavaScript); o CDN da
   * Rekognition serve o WASM e o modelo, e o streaming leva o vídeo da prova. Mudou aqui, mude
   * {@code CspDaVerificacaoTest} e {@code cadeiaDeSuprimentos.test.ts}.
   */
  public static final String CSP_VERIFICACAO =
      "default-src 'none'; script-src 'self' 'wasm-unsafe-eval'; style-src 'self'; "
          + "img-src 'self'; font-src 'self'; media-src 'self'; "
          + "connect-src 'self' blob: https://cdn.liveness.rekognition.amazonaws.com "
          + "wss://streaming-rekognition.us-east-1.amazonaws.com; manifest-src 'self'; "
          + "worker-src 'none'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'; "
          + "object-src 'none'; require-trusted-types-for 'script'; trusted-types 'none'";

  /**
   * A prova de vida usa só a câmera; o microfone e a localização ficam fechados por menor
   * privilégio.
   */
  public static final String PERMISSIONS_POLICY_VERIFICACAO =
      "camera=(self), microphone=(), geolocation=()";

  /**
   * Nenhuma outra resposta usa câmera nem microfone; a localização fica liberada só para a própria
   * origem (a área aproximada das preferências de encontro, com consentimento na tela).
   */
  public static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(self)";

  /**
   * O index.html, o service worker, o manifest e as rotas do SPA, que o Spring encaminha ao
   * index.html: o filtro não roda de novo no forward, então vale o caminho pedido.
   */
  private static final Set<String> SEMPRE_REVALIDADOS =
      Stream.concat(
              Stream.of("/", "/index.html", "/sw.js", "/manifest.webmanifest"),
              RotasDoSpa.CAMINHOS.stream())
          .collect(Collectors.toUnmodifiableSet());

  private static boolean sempreRevalidado(String caminho) {
    return SEMPRE_REVALIDADOS.contains(caminho)
        || RotasDoSpa.PREFIXOS.stream().anyMatch(caminho::startsWith);
  }

  /**
   * Todos os cabeçalhos entram por {@code setHeader}, antes da cadeia: quem responde depois (como a
   * foto borrada, com cache privado) troca o valor com outro {@code setHeader}, sem deixar dois.
   */
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    boolean verificacao = RotasDoSpa.CAMINHO_DA_VERIFICACAO.equals(request.getRequestURI());
    response.setHeader("Content-Security-Policy", verificacao ? CSP_VERIFICACAO : CSP);
    response.setHeader(
        "Permissions-Policy", verificacao ? PERMISSIONS_POLICY_VERIFICACAO : PERMISSIONS_POLICY);
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.setHeader("Referrer-Policy", "no-referrer");
    response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
    if (MesmaOrigem.ehApi(request)) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    } else if (sempreRevalidado(request.getRequestURI())) {
      response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
    }
    chain.doFilter(request, response);
  }
}
