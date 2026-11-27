package bipotheking.net.duora.conta;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * O que outros módulos sabem de uma conta. {@code verificada} é sempre {@code false} na Etapa 2a. O
 * {@code toString} omite a data de nascimento, que nunca vai para o log.
 */
public record ContaResumo(
    ContaId id,
    EstadoConta estado,
    @Nullable LocalDate dataDeNascimento,
    boolean verificada,
    @Nullable Instant ativadaEm) {

  /**
   * Anos completos em {@code hoje}; vazio enquanto o cadastro não gravou a data de nascimento.
   *
   * @throws IllegalArgumentException se {@code hoje} vem antes do nascimento (relógio ou dado
   *     errado: não há idade que faça sentido)
   */
  public OptionalInt idadeEm(LocalDate hoje) {
    LocalDate nascimento = dataDeNascimento;
    if (nascimento == null) {
      return OptionalInt.empty();
    }
    if (hoje.isBefore(nascimento)) {
      throw new IllegalArgumentException("A data de referência vem antes do nascimento");
    }
    return OptionalInt.of(Period.between(nascimento, hoje).getYears());
  }

  @Override
  public String toString() {
    return "ContaResumo[id=%s, estado=%s, verificada=%s, ativadaEm=%s]"
        .formatted(id, estado, verificada, ativadaEm);
  }
}
