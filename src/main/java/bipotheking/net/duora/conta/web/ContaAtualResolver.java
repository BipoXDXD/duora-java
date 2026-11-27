package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Entrega ao parâmetro {@code @ContaAtual ContaId} a conta que o {@link ExigeSessao} validou. */
class ContaAtualResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parametro) {
    return parametro.hasParameterAnnotation(ContaAtual.class)
        && ContaId.class.equals(parametro.getParameterType());
  }

  /**
   * Sem a conta no atributo, o handler não passou pelo {@link ExigeSessao}: é rota pública com
   * {@code @ContaAtual}, defeito de código, e falha em vez de seguir sem conta.
   */
  @Override
  public ContaId resolveArgument(
      MethodParameter parametro,
      @Nullable ModelAndViewContainer mavContainer,
      NativeWebRequest requisicao,
      @Nullable WebDataBinderFactory binderFactory) {
    Object conta =
        requisicao.getAttribute(ExigeSessao.ATRIBUTO_CONTA, RequestAttributes.SCOPE_REQUEST);
    if (conta instanceof ContaId id) {
      return id;
    }
    throw new IllegalStateException(
        "@ContaAtual numa rota que não passou pelo ExigeSessao: " + parametro.getMethod());
  }
}
