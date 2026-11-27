package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.DenunciasPublicas;
import bipotheking.net.duora.conta.DenunciasPublicas.RespostaDeDenuncia;
import bipotheking.net.duora.web.OrigemDaRequisicao;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Canal público de denúncia: rota da lista pública, sem sessão; como toda mutação, exige o {@code
 * X-Duora} e a mesma origem.
 */
@RestController
class DenunciaPublicaController {

  private final DenunciasPublicas denunciasPublicas;
  private final OrigemDaRequisicao origem;

  DenunciaPublicaController(DenunciasPublicas denunciasPublicas, OrigemDaRequisicao origem) {
    this.denunciasPublicas = denunciasPublicas;
    this.origem = origem;
  }

  /**
   * O {@code motivo} chega como texto: um motivo desconhecido é recusa de negócio (422), não corpo
   * inválido (400). O Spring MVC registra o corpo em DEBUG: o {@code toString} não mostra nada do
   * que veio, nem o motivo, que ainda é texto livre aqui.
   */
  record DenunciaPublicaJson(
      @Nullable String motivo,
      @Nullable String descricao,
      @Nullable String referencia,
      @Nullable String emailContato,
      @Nullable String tokenVerificacao) {

    @Override
    public String toString() {
      return "DenunciaPublicaJson[motivo=***, descricao=***, referencia=***, emailContato=***,"
          + " tokenVerificacao=***]";
    }
  }

  @PostMapping("/api/denuncias-publicas")
  @ResponseStatus(HttpStatus.ACCEPTED)
  RespostaDeDenuncia denunciar(@RequestBody DenunciaPublicaJson corpo, HttpServletRequest request) {
    return denunciasPublicas.registrar(
        corpo.motivo(),
        corpo.descricao(),
        corpo.referencia(),
        corpo.emailContato(),
        corpo.tokenVerificacao(),
        origem.de(request).ip());
  }
}
