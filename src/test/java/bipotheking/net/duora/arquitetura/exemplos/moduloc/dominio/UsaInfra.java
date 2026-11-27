package bipotheking.net.duora.arquitetura.exemplos.moduloc.dominio;

import bipotheking.net.duora.arquitetura.exemplos.moduloc.infra.Repositorio;

/** Viola o domínio puro: depende da {@code infra} do próprio módulo. */
class UsaInfra {

  private final Repositorio repositorio = new Repositorio();

  int contar() {
    return repositorio.contar();
  }
}
