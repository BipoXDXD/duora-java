package bipotheking.net.duora.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Rota que só existe nos testes: consome a regra {@code teste} pelo IP de origem. */
@RestController
class ControllerDeTesteDeLimite {

  private final OrigemDaRequisicao origemDaRequisicao;
  private final LimiteDeTaxa limiteDeTaxa;

  ControllerDeTesteDeLimite(OrigemDaRequisicao origemDaRequisicao, LimiteDeTaxa limiteDeTaxa) {
    this.origemDaRequisicao = origemDaRequisicao;
    this.limiteDeTaxa = limiteDeTaxa;
  }

  @GetMapping("/teste/limite")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void limite(HttpServletRequest request) {
    limiteDeTaxa.consumir("teste", origemDaRequisicao.de(request).ip());
  }
}
