package bipotheking.net.duora.sala;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.EstadoInicial;
import bipotheking.net.duora.eventos.Evento;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Ao abrir o stream, quem está na fila recebe o {@code fila.estado} atual; quem não está, nada.
 *
 * <p>A {@link Fila} entra {@code @Lazy}: ela publica pelo {@code CanalDeEventos}, que depende da
 * entrega dos streams, que depende deste estado inicial. Sem o proxy, o ciclo derruba o boot.
 */
@Component
public class EstadoInicialDaFila implements EstadoInicial {

  private final Fila fila;

  EstadoInicialDaFila(@Lazy Fila fila) {
    this.fila = fila;
  }

  @Override
  public List<Evento> para(ContaId conta) {
    return fila.estado(conta)
        .map(estado -> List.of(new Evento(Fila.EVENTO_ESTADO, estado)))
        .orElseGet(List::of);
  }
}
