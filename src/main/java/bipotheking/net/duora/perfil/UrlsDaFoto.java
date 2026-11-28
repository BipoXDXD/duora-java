package bipotheking.net.duora.perfil;

import bipotheking.net.duora.web.Variante;

/** As URLs das duas variantes de uma foto. */
public record UrlsDaFoto(String nitida, String borrada) {

  static UrlsDaFoto de(FotoId foto) {
    return new UrlsDaFoto(
        UrlDaFoto.de(foto, Variante.NITIDA), UrlDaFoto.de(foto, Variante.BORRADA));
  }
}
