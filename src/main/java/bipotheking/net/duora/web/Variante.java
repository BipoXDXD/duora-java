package bipotheking.net.duora.web;

/** As duas versões guardadas de cada imagem: a nítida e a borrada, que não guarda detalhe. */
public enum Variante {
  NITIDA("nitida"),
  BORRADA("borrada");

  private final String sufixo;

  Variante(String sufixo) {
    this.sufixo = sufixo;
  }

  /** Último segmento da URL da variante ({@code /api/fotos/<id>/<sufixo>}). */
  public String sufixo() {
    return sufixo;
  }
}
