package bipotheking.net.duora.conta.web;

import bipotheking.net.duora.conta.CodigoSessao;
import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.RotasPublicas;
import bipotheking.net.duora.conta.SessaoAtiva;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.web.CodigoWeb;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Negação por padrão em {@code /api/**}: fora de {@link RotasPublicas}, toda rota exige sessão
 * válida de conta disponível, inclusive rota inexistente. A conta da sessão fica num atributo da
 * requisição, que o {@link ContaAtualResolver} entrega ao handler.
 */
@Component
class ExigeSessao implements HandlerInterceptor {

  static final String ATRIBUTO_CONTA = ExigeSessao.class.getName() + ".conta";
  private static final String NAO_AUTENTICADO = "Entre para continuar.";

  private final List<PathPattern> publicas =
      RotasPublicas.PADROES.stream().map(PathPatternParser.defaultInstance::parse).toList();
  private final SessaoDaRequisicao sessaoDaRequisicao;

  ExigeSessao(SessaoDaRequisicao sessaoDaRequisicao) {
    this.sessaoDaRequisicao = sessaoDaRequisicao;
  }

  /**
   * Rota pública passa sem ler o cookie. As recusas saem pelo {@code TratamentoDeErros}; o cookie
   * expirado de uma sessão inválida já está na resposta quando a exceção sobe.
   */
  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (publica(request)) {
      return true;
    }
    SessaoAtiva sessao =
        sessaoDaRequisicao
            .ler(request, response)
            .orElseThrow(() -> new ErroDeNegocio(CodigoWeb.NAO_AUTENTICADO, NAO_AUTENTICADO));
    switch (sessao.estado()) {
      case SUSPENSA, BANIDA, EXCLUIDA -> throw recusa(CodigoSessao.CONTA_INDISPONIVEL);
      case PENDENTE, AGUARDANDO_VERIFICACAO -> {
        if (exigeCadastroConcluido(handler)) {
          throw recusa(CodigoSessao.CADASTRO_INCOMPLETO);
        }
      }
      case ATIVA -> {
        // Conta ativa segue para qualquer rota protegida.
      }
    }
    request.setAttribute(ATRIBUTO_CONTA, sessao.conta());
    return true;
  }

  private boolean publica(HttpServletRequest request) {
    PathContainer caminho =
        RequestPath.parse(request.getRequestURI(), request.getContextPath())
            .pathWithinApplication();
    return publicas.stream().anyMatch(padrao -> padrao.matches(caminho));
  }

  /**
   * Só um handler que declara {@code @ContaAtual(cadastroConcluido = false)} aceita cadastro por
   * concluir; qualquer outro (rota inexistente incluída) exige o cadastro concluído.
   */
  private static boolean exigeCadastroConcluido(Object handler) {
    if (!(handler instanceof HandlerMethod metodo)) {
      return true;
    }
    return Arrays.stream(metodo.getMethodParameters())
        .map(parametro -> parametro.getParameterAnnotation(ContaAtual.class))
        .noneMatch(anotacao -> anotacao != null && !anotacao.cadastroConcluido());
  }

  static ErroDeNegocio recusa(CodigoSessao codigo) {
    return new ErroDeNegocio(codigo, codigo.mensagem());
  }
}
