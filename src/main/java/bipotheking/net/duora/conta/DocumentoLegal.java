package bipotheking.net.duora.conta;

import java.util.Arrays;
import java.util.Optional;

/** Documento legal versionado que a pessoa aceita no cadastro. */
public enum DocumentoLegal {
  TERMOS("termos"),
  PRIVACIDADE("privacidade");

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
