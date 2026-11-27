package bipotheking.net.duora.conta.infra;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chaves do Cloudflare Turnstile ({@code duora.turnstile.*}). Dev e test usam as chaves de teste
 * públicas da Cloudflare; prod lê as reais do ambiente.
 */
@ConfigurationProperties("duora.turnstile")
record TurnstileConfig(
    String chaveDoSite,
    String segredo,
    @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") URI url) {}
