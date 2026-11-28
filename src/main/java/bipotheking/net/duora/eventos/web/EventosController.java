package bipotheking.net.duora.eventos.web;

import bipotheking.net.duora.conta.ContaAtual;
import bipotheking.net.duora.conta.ContaId;
import java.util.OptionalLong;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@code GET /api/eventos}: o stream SSE único da sessão (ADR 0002). Quem volta diz o último {@code
 * id} recebido pelo {@code Last-Event-ID} (o {@code EventSource} manda sozinho) ou por {@code
 * ?ultimoId=} (quem recria o {@code EventSource}); o cabeçalho vence.
 */
@RestController
class EventosController {

  private final StreamsSse streams;

  EventosController(StreamsSse streams) {
    this.streams = streams;
  }

  /** Sem buffer no proxy ({@code X-Accel-Buffering}) e sem cache: cada evento sai na hora. */
  @GetMapping(path = "/api/eventos", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  ResponseEntity<SseEmitter> abrir(
      @ContaAtual ContaId conta,
      @RequestHeader(name = "Last-Event-ID", required = false) @Nullable String cabecalho,
      @RequestParam(name = "ultimoId", required = false) @Nullable String consulta) {
    OptionalLong ultimo = numero(cabecalho);
    if (ultimo.isEmpty()) {
      ultimo = numero(consulta);
    }
    return ResponseEntity.ok()
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Accel-Buffering", "no")
        .body(streams.abrir(conta, ultimo));
  }

  /** Um número inteiro maior ou igual a zero; qualquer outra coisa é ignorada. */
  private static OptionalLong numero(@Nullable String bruto) {
    if (bruto == null) {
      return OptionalLong.empty();
    }
    try {
      long valor = Long.parseLong(bruto.strip());
      return valor >= 0 ? OptionalLong.of(valor) : OptionalLong.empty();
    } catch (NumberFormatException e) {
      return OptionalLong.empty();
    }
  }
}
