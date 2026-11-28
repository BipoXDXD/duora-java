package bipotheking.net.duora.conta;

import java.util.Arrays;
import java.util.Optional;

/**
 * Documento legal versionado que a pessoa aceita: termos e privacidade no cadastro, o consentimento
 * biométrico ao abrir a primeira sessão de verificação de idade de cada versão.
 */
public enum DocumentoLegal {
  TERMOS("termos"),
  PRIVACIDADE("privacidade"),
  BIOMETRIA("biometria");

  private final String caminho;

  DocumentoLegal(String caminho) {
    this.caminho = caminho;
  }

  /** Nome na URL ({@code /api/legal/<caminho>}) e no arquivo ({@code <caminho>.md}). */
  public String caminho() {
    return caminho;
  }

  public static Optional<DocumentoLegal> doCaminho(String caminho) {
    return Arrays.stream(values()).filter(d -> d.caminho.equals(caminho)).findFirst();
  }
}
