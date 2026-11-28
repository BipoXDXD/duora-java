package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.ContaId;
import java.util.Objects;

/** Um evento endereçado a uma conta. */
public record Envio(ContaId para, Evento evento) {

  public Envio {
    Objects.requireNonNull(para, "para");
    Objects.requireNonNull(evento, "evento");
  }
}
