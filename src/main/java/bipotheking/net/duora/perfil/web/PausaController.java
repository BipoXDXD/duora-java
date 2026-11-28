package bipotheking.net.duora.perfil.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.PausaDaConta;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Pausa e retomada da conta da sessão; as duas rotas são idempotentes. */
@RestController
@RequestMapping("/api/perfil/pausa")
class PausaController {

  private final PausaDaConta pausa;

  PausaController(PausaDaConta pausa) {
    this.pausa = pausa;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void pausar(@ContaAtual ContaId conta) {
    pausa.pausar(conta);
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void retomar(@ContaAtual ContaId conta) {
    pausa.retomar(conta);
  }
}
