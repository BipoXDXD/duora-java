package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte recusas em {@code application/problem+json} com o campo {@code codigo}. É o único ponto
 * que traduz um {@link TipoDeErro} em status HTTP.
 */
@RestControllerAdvice
class TratamentoDeErros extends ResponseEntityExceptionHandler {

  private static final Logger LOG = LoggerFactory.getLogger(TratamentoDeErros.class);
  private static final URI SEM_TIPO = URI.create("about:blank");

  static HttpStatus status(TipoDeErro tipo) {
    return switch (tipo) {
      case ENTRADA_INVALIDA -> HttpStatus.BAD_REQUEST;
      case NAO_AUTENTICADO -> HttpStatus.UNAUTHORIZED;
      case PROIBIDO -> HttpStatus.FORBIDDEN;
      case NAO_ENCONTRADO -> HttpStatus.NOT_FOUND;
      case METODO_NAO_PERMITIDO -> HttpStatus.METHOD_NOT_ALLOWED;
      case CONFLITO -> HttpStatus.CONFLICT;
      case ENCERRADO -> HttpStatus.GONE;
      case GRANDE_DEMAIS -> HttpStatus.CONTENT_TOO_LARGE;
      case FORMATO_NAO_SUPORTADO -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      case ENDERECO_RECUSADO -> HttpStatus.MISDIRECTED_REQUEST;
      case REGRA_DE_NEGOCIO -> HttpStatus.UNPROCESSABLE_CONTENT;
      case LIMITE -> HttpStatus.TOO_MANY_REQUESTS;
      case INTERNO -> HttpStatus.INTERNAL_SERVER_ERROR;
      case INDISPONIVEL -> HttpStatus.SERVICE_UNAVAILABLE;
    };
  }

  @ExceptionHandler(ErroDeNegocio.class)
  ResponseEntity<ProblemDetail> recusa(ErroDeNegocio erro) {
    ProblemDetail problema = problema(erro.codigo(), erro.getMessage());
    erro.extras().forEach(problema::setProperty);
    return ResponseEntity.status(status(erro.codigo().tipo())).body(problema);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> inesperado(Exception erro) {
    LOG.error("Falha inesperada ao atender a requisição", erro);
    CodigoDeErro codigo = CodigoWeb.ERRO_INTERNO;
    return ResponseEntity.status(status(codigo.tipo()))
        .body(problema(codigo, "Algo deu errado. Tente de novo."));
  }

  @Override
  protected @Nullable ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    CodigoDeErro codigo = CodigoWeb.CORPO_INVALIDO;
    return ResponseEntity.status(status(codigo.tipo()))
        .body(problema(codigo, "O corpo da requisição não está no formato esperado."));
  }

  static ProblemDetail problema(CodigoDeErro codigo, String detalhe) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(status(codigo.tipo()), detalhe);
    problema.setType(SEM_TIPO);
    problema.setTitle(codigo.titulo());
    problema.setProperty("codigo", codigo.name());
    return problema;
  }
}
