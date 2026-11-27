package bipotheking.net.duora.arquitetura.exemplos.moduloc.dominio;

import org.springframework.http.HttpStatus;

/** Viola o domínio puro: conhece status HTTP fora de {@code web}. */
class UsaHttp {

  HttpStatus status() {
    return HttpStatus.OK;
  }
}
