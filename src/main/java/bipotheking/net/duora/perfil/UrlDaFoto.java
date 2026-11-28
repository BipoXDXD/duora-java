package bipotheking.net.duora.perfil;

import bipotheking.net.duora.web.Variante;

/** O endereço de uma variante de foto na API. */
public final class UrlDaFoto {

  private UrlDaFoto() {}

  /** {@code /api/fotos/<uuid>/<sufixo>}. */
  public static String de(FotoId foto, Variante variante) {
    return "/api/fotos/" + foto.valor() + "/" + variante.sufixo();
  }
}
