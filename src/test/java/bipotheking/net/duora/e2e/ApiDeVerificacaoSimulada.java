package bipotheking.net.duora.e2e;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Route;
import java.util.HashMap;
import java.util.Map;

/**
 * A API da verificação de idade respondida pelo Playwright, sem servidor de verdade por trás: a
 * conta aguardando verificação, a situação dela e as três ações (sessão, resultado da selfie e
 * envio do documento). Cada resposta pode ser trocada antes ou depois de abrir a página; as versões
 * legais e o resto da API seguem para o servidor do teste.
 */
final class ApiDeVerificacaoSimulada {

  static final String SITUACAO_PADRAO =
      """
      {"estado":"AGUARDANDO","tentativasRestantesHoje":3,"enviosDeDocumentoRestantesHoje":2,\
      "modo":"falso"}\
      """;

  private static final String CONTA_AGUARDANDO =
      """
      {"estado":"AGUARDANDO_VERIFICACAO","cadastroConcluido":false,"verificada":false}\
      """;

  /** Credencial fictícia, no formato da do STS; nunca chega a uma conta da AWS. */
  private static final String SESSAO_PADRAO =
      """
      {"sessaoId":"sessao-simulada","regiao":"us-east-1","modo":"falso",\
      "credencial":{"accessKeyId":"ASIASIMULADA","secretAccessKey":"segredo-simulado",\
      "sessionToken":"token-simulado","expiraEm":"2099-01-01T00:00:00Z"}}\
      """;

  private static final String APROVADA =
      """
      {"resultado":"APROVADA","pedeDocumento":false,"tentativasRestantesHoje":2,\
      "enviosDeDocumentoRestantesHoje":2}\
      """;

  private Resposta situacao = Resposta.json(200, SITUACAO_PADRAO);
  private Resposta sessao = Resposta.json(201, SESSAO_PADRAO);
  private Resposta resultado = Resposta.json(200, APROVADA);
  private Resposta documento = Resposta.json(200, APROVADA);

  private ApiDeVerificacaoSimulada() {}

  /** Instala as rotas na página; chame antes de navegar. */
  static ApiDeVerificacaoSimulada instalar(Page page) {
    ApiDeVerificacaoSimulada api = new ApiDeVerificacaoSimulada();
    page.route("**/api/conta", rota -> Resposta.json(200, CONTA_AGUARDANDO).responder(rota));
    page.route("**/api/conta/verificacao", rota -> api.situacao.responder(rota));
    page.route("**/api/conta/verificacao/sessao", rota -> api.sessao.responder(rota));
    page.route(
        "**/api/conta/verificacao/sessao/*/resultado", rota -> api.resultado.responder(rota));
    page.route("**/api/conta/verificacao/documento", rota -> api.documento.responder(rota));
    return api;
  }

  /** O corpo do {@code GET /api/conta/verificacao}, sempre 200. */
  ApiDeVerificacaoSimulada comSituacao(String json) {
    situacao = Resposta.json(200, json);
    return this;
  }

  ApiDeVerificacaoSimulada comSessao(Resposta resposta) {
    sessao = resposta;
    return this;
  }

  ApiDeVerificacaoSimulada comResultado(Resposta resposta) {
    resultado = resposta;
    return this;
  }

  ApiDeVerificacaoSimulada comDocumento(Resposta resposta) {
    documento = resposta;
    return this;
  }

  /** Uma resposta da API: status, corpo JSON e cabeçalhos extras. */
  record Resposta(int status, String corpo, Map<String, String> cabecalhos) {

    Resposta {
      cabecalhos = Map.copyOf(cabecalhos);
    }

    static Resposta json(int status, String corpo) {
      return new Resposta(status, corpo, Map.of());
    }

    /** O problem+json do {@code TratamentoDeErros}, com o código e a mensagem do contrato. */
    static Resposta problema(int status, String codigo, String mensagem) {
      String corpo =
          """
          {"type":"about:blank","title":"Erro","status":%d,"detail":"%s",\
          "codigo":"%s","mensagem":"%s"}\
          """
              .formatted(status, mensagem, codigo, mensagem);
      return new Resposta(status, corpo, Map.of());
    }

    Resposta comCabecalho(String nome, String valor) {
      Map<String, String> novos = new HashMap<>(cabecalhos);
      novos.put(nome, valor);
      return new Resposta(status, corpo, novos);
    }

    void responder(Route rota) {
      rota.fulfill(
          new Route.FulfillOptions()
              .setStatus(status)
              .setContentType(status >= 400 ? "application/problem+json" : "application/json")
              .setHeaders(cabecalhos)
              .setBody(corpo));
    }
  }
}
