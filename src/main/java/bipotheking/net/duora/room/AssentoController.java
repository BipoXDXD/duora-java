package bipotheking.net.duora.room;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Assento de um encontro: a conta da sessão recebe o cookie do assento dela. Encontro inexistente,
 * encerrado ou de outras contas responde o mesmo 404, para o id não revelar nada.
 */
@RestController
@RequestMapping("/api/encontros")
class AssentoController {

  private final Salas salas;

  AssentoController(Salas salas) {
    this.salas = salas;
  }

  @PostMapping("/{id}/assento")
  ResponseEntity<Visao> reivindicar(
      @ContaAtual ContaId conta, @PathVariable("id") String encontroId) {
    Salas.Entrada entrada = salas.reivindicar(encontroId, conta);
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, CookieAssento.emitir(entrada.token()))
        .body(entrada.visao());
  }
}
