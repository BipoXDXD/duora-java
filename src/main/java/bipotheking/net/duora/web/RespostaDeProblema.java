package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Escreve um problem+json direto na resposta, para filtros que agem antes do Spring MVC. */
public final class RespostaDeProblema {

  private RespostaDeProblema() {}

  public static void escrever(
      HttpServletResponse response, JsonMapper json, CodigoDeErro codigo, String mensagem)
      throws IOException {
    ProblemDetail problema = TratamentoDeErros.problema(codigo, mensagem);
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("type", String.valueOf(problema.getType()));
    corpo.put("title", problema.getTitle());
    corpo.put("status", problema.getStatus());
    corpo.put("detail", problema.getDetail());
    corpo.put("codigo", codigo.name());
    response.setStatus(problema.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    json.writeValue(response.getOutputStream(), corpo);
  }
}
