package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import java.util.Objects;

/**
 * Sessão de prova de vida aberta para o navegador capturar a selfie. Só vive em memória durante a
 * requisição; o {@code toString} da credencial não mostra o segredo nem o token.
 *
 * @param regiao sempre {@code us-east-1}
 */
public record SessaoDeCaptura(
    String sessaoId, String regiao, ModoDeCaptura modo, CredencialCurta credencial) {

  public SessaoDeCaptura {
    Objects.requireNonNull(sessaoId, "sessaoId");
    Objects.requireNonNull(regiao, "regiao");
    Objects.requireNonNull(modo, "modo");
    Objects.requireNonNull(credencial, "credencial");
  }
}
