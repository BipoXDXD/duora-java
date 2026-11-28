package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;
import java.net.URI;
import java.time.Duration;
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
    ResponseEntity.BodyBuilder resposta = ResponseEntity.status(status(erro.codigo().tipo()));
    erro.tentarDeNovoEm()
        .ifPresent(espera ->
            resposta.header(
                HttpHeaders.RETRY_AFTER, String.valueOf(segundosArredondadosParaCima(espera))));
    return resposta.body(problema);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ProblemDetail> inesperado(Exception erro) {
    LOG.error("Falha inesperada ao atender a requisição", erro);
    CodigoWeb codigo = CodigoWeb.ERRO_INTERNO;
    return ResponseEntity.status(status(codigo.tipo()))
        .body(problema(codigo, mensagemPadrao(codigo)));
  }

  /**
   * Exceções que o próprio Spring MVC trata (rota, método, formato, cabeçalho, corpo): mantém o
   * status e os cabeçalhos dele e troca o corpo por um com {@code codigo} e {@code mensagem}.
   */
  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      @Nullable Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    CodigoWeb codigo = codigoDoSpring(ex, statusCode);
    if (statusCode.is5xxServerError()) {
      LOG.error("Falha do Spring MVC ao atender a requisição", ex);
    }
    ProblemDetail problema = problema(codigo, mensagemPadrao(codigo), statusCode);
    return super.handleExceptionInternal(ex, problema, headers, statusCode, request);
  }

  static ProblemDetail problema(CodigoDeErro codigo, String mensagem) {
    return problema(codigo, mensagem, status(codigo.tipo()));
  }

  private static ProblemDetail problema(
      CodigoDeErro codigo, String mensagem, HttpStatusCode status) {
    ProblemDetail problema = ProblemDetail.forStatusAndDetail(status, mensagem);
    problema.setType(SEM_TIPO);
    problema.setTitle(codigo.titulo());
    problema.setProperty("codigo", codigo.name());
    problema.setProperty("mensagem", mensagem);
    return problema;
  }

  private static CodigoWeb codigoDoSpring(Exception ex, HttpStatusCode status) {
    if (ex instanceof HttpMessageNotReadableException) {
      return CodigoWeb.CORPO_INVALIDO;
    }
    return switch (status.value()) {
      case 404 -> CodigoWeb.RECURSO_INEXISTENTE;
      case 405 -> CodigoWeb.METODO_NAO_PERMITIDO;
      // Só o multipart dispara o 413 do Spring, e todo arquivo enviado é imagem (foto, documento).
      case 413 -> CodigoWeb.IMAGEM_GRANDE_DEMAIS;
      case 415 -> CodigoWeb.FORMATO_NAO_SUPORTADO;
      default -> status.is5xxServerError() ? CodigoWeb.ERRO_INTERNO : CodigoWeb.REQUISICAO_INVALIDA;
    };
  }

  private static String mensagemPadrao(CodigoWeb codigo) {
    return switch (codigo) {
      case RECURSO_INEXISTENTE -> "Não encontramos este endereço.";
      case METODO_NAO_PERMITIDO -> "Este endereço não aceita esse tipo de pedido.";
      case FORMATO_NAO_SUPORTADO -> "Envie o conteúdo num formato aceito.";
      case IMAGEM_GRANDE_DEMAIS -> ProcessadorDeImagemVips.MENSAGEM_GRANDE_DEMAIS;
      case CORPO_INVALIDO -> "O corpo da requisição não está no formato esperado.";
      case ERRO_INTERNO -> "Algo deu errado. Tente de novo.";
      default -> "A requisição veio incompleta ou com um valor inválido.";
    };
  }

  /**
   * {@code Retry-After} só aceita segundos inteiros; arredondar para baixo convidaria o cliente a
   * voltar cedo demais.
   */
  private static long segundosArredondadosParaCima(Duration espera) {
    long segundos = espera.getSeconds() + (espera.getNano() > 0 ? 1 : 0);
    return Math.max(0, segundos);
  }
}
