package bipotheking.net.duora.conta;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota protegida que só existe nos testes: exige sessão de conta com o cadastro concluído. */
@RestController
class ControllerDeTesteDeSessao {

  @GetMapping("/api/teste/so-ativa")
  ResponseEntity<String> soAtiva(@ContaAtual ContaId conta) {
    return ResponseEntity.ok(conta.toString());
  }
}
