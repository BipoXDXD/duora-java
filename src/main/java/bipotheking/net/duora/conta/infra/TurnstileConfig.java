package bipotheking.net.duora.conta.infra;

import static bipotheking.net.duora.conta.infra.Exigencias.exigir;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chaves do Cloudflare Turnstile ({@code duora.turnstile.*}). Dev e test usam as chaves de teste
 * públicas da Cloudflare; prod lê as reais do ambiente. Chave ausente, em branco ou com placeholder
 * sem resolver derruba o boot: o Binder deixa passar {@code ${...}} literal, e o Turnstile
 * recusaria todo login sem aviso.
 */
@ConfigurationProperties("duora.turnstile")
record TurnstileConfig(
    String chaveDoSite,
    String segredo,
    @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") URI url) {

  TurnstileConfig {
    exigir(chaveDoSite, "duora.turnstile.chave-do-site");
    exigir(segredo, "duora.turnstile.segredo");
  }
}
