package bipotheking.net.duora.arquitetura.exemplos.moduloc.dominio;

import bipotheking.net.duora.arquitetura.exemplos.moduloc.web.Controlador;

/** Viola o domínio puro: depende da camada {@code web} do próprio módulo. */
class UsaWeb {

  private final Controlador controlador = new Controlador();

  String responder() {
    return controlador.responder();
  }
}
