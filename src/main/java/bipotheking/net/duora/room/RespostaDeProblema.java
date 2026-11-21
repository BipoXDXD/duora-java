package bipotheking.net.duora.room;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Escreve um problem+json direto na resposta, para filtros que agem antes do Spring MVC. */
final class RespostaDeProblema {

  private RespostaDeProblema() {}

  static void escrever(HttpServletResponse response, JsonMapper json, Codigo codigo, String detalhe)
      throws IOException {
    ProblemDetail problema = TratamentoDeErros.problema(codigo, detalhe);
    Map<String, Object> corpo = new LinkedHashMap<>();
    corpo.put("type", String.valueOf(problema.getType()));
    corpo.put("title", problema.getTitle());
    corpo.put("status", problema.getStatus());
    corpo.put("detail", problema.getDetail());
    corpo.put("codigo", codigo.name());
    response.setStatus(codigo.status().value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    json.writeValue(response.getOutputStream(), corpo);
  }
}
