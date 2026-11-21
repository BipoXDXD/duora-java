package bipotheking.net.duora.room;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Converte recusas em {@code application/problem+json} com o campo {@code codigo}. */
@RestControllerAdvice
class TratamentoDeErros extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TratamentoDeErros.class);
  private static final URI SEM_TIPO = URI.create("about:blank");

  @ExceptionHandler(ErroApi.class)
  ResponseEntity<ProblemDetail> recusa(ErroApi erro) {
    ProblemDetail problema = problema(erro.codigo(), erro.getMessage());
    erro.visao().ifPresent(visao -> problema.setProperty("visao", visao));
    erro.motivo().ifPresent(motivo -> problema.setProperty("motivo", motivo.name()));
    return ResponseEntity.status(erro.codigo().status()).body(problema);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> inesperado(Exception erro) {
    LOG.error("Falha inesperada ao atender a requisição", erro);
    Codigo codigo = Codigo.ERRO_INTERNO;
    return ResponseEntity.status(codigo.status())
        .body(problema(codigo, "Algo deu errado. Tente de novo."));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Codigo codigo = Codigo.CORPO_INVALIDO;
    return ResponseEntity.status(codigo.status())
        .body(problema(codigo, "O corpo da requisição não está no formato esperado."));
  }

  static ProblemDetail problema(Codigo codigo, String detalhe) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.status(), detalhe);
    problema.setType(SEM_TIPO);
    problema.setTitle(codigo.titulo());
    problema.setProperty("codigo", codigo.name());
    return problema;
  }
}
