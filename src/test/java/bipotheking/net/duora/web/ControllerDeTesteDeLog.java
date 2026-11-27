package bipotheking.net.duora.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Rota que só existe nos testes: escreve uma linha de log conhecida dentro de uma requisição. */
@RestController
class ControllerDeTesteDeLog {

  static final String MENSAGEM = "pedido-de-teste-correlacao";

  private static final Logger LOG = LoggerFactory.getLogger(ControllerDeTesteDeLog.class);

  @GetMapping("/teste/log")
  String log() {
    LOG.info(MENSAGEM);
    return "ok";
  }
}
