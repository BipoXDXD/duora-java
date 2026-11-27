package bipotheking.net.duora.conta.dominio;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resultado de {@link RepositorioDeVerificacoes#reservar}: a tentativa gravada ou o limite do dia.
 */
public sealed interface ReservaDeTentativa {

  /** A tentativa ficou gravada, ainda sem sessão. */
  record Feita(UUID tentativa) implements ReservaDeTentativa {
    public Feita {
      Objects.requireNonNull(tentativa, "tentativa");
    }
  }

  /**
   * A finalidade já está no limite das últimas 24 h; libera quando a mais antiga sair da janela.
   */
  record Esgotada(Instant liberaEm) implements ReservaDeTentativa {
    public Esgotada {
      Objects.requireNonNull(liberaEm, "liberaEm");
    }
  }
}
