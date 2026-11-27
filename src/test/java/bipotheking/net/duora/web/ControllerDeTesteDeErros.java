package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.io.Serial;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rotas que só existem nos testes, para exercitar o tratamento de erros. Ficam em {@code /teste},
 * fora de {@code /api}, para não depender de sessão.
 */
@RestController
@RequestMapping("/teste/erros")
class ControllerDeTesteDeErros {

  @GetMapping("/negocio")
  void negocio() {
    throw new ErroDeNegocio(CodigoWeb.NAO_AUTENTICADO, "Entre para continuar.");
  }

  @GetMapping("/extras")
  void extras() {
    throw new ErroComExtras(
        CodigoWeb.REQUISICAO_INVALIDA,
        "Veja a visão atual.",
        Map.of("visao", Map.of("fase", "MONTANDO")),
        null);
  }

  @GetMapping("/limite")
  void limite() {
    throw new ErroComExtras(
        CodigoWeb.LIMITE_DE_TAXA, "Espere um pouco.", Map.of(), Duration.ofMillis(899_200));
  }

  @GetMapping("/inesperado")
  void inesperado(@RequestParam String canario) {
    throw new IllegalStateException(canario);
  }

  @PostMapping(path = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
  Map<String, Object> json(@RequestBody Map<String, Object> corpo) {
    return corpo;
  }

  @GetMapping("/so-get")
  String soGet() {
    return "ok";
  }

  @GetMapping("/cabecalho")
  String cabecalho(@RequestHeader("X-Obrigatorio") String valor) {
    return valor;
  }

  private static final class ErroComExtras extends ErroDeNegocio {

    @Serial private static final long serialVersionUID = 1L;

    ErroComExtras(
        CodigoWeb codigo,
        String mensagem,
        Map<String, Object> extras,
        @Nullable Duration tentarDeNovoEm) {
      super(codigo, mensagem, extras, tentarDeNovoEm);
    }
  }
}
