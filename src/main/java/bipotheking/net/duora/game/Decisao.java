package bipotheking.net.duora.game;

import java.util.Objects;

/** Resposta das regras a um pedido: o novo estado ou o motivo da recusa. */
public sealed interface Decisao {

  record Aceita(Estado estado) implements Decisao {
    public Aceita {
      Objects.requireNonNull(estado);
    }
  }

  record Recusada(Recusa recusa) implements Decisao {
    public Recusada {
      Objects.requireNonNull(recusa);
    }
  }
}
