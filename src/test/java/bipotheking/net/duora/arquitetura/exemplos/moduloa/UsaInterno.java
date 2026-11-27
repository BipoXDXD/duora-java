package bipotheking.net.duora.arquitetura.exemplos.moduloa;

import bipotheking.net.duora.arquitetura.exemplos.modulob.infra.Interno;

/** Viola a API pública: depende de {@code modulob.infra}, não da raiz de {@code modulob}. */
class UsaInterno {

  Interno interno() {
    return new Interno();
  }
}
