package bipotheking.net.duora.conta;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.OptionalInt;
import org.jspecify.annotations.Nullable;

/**
 * O que outros módulos sabem de uma conta. {@code verificada} é sempre {@code false} na Etapa 2a.
 */
public record ContaResumo(
    ContaId id,
    EstadoConta estado,
    @Nullable LocalDate dataDeNascimento,
    boolean verificada,
    @Nullable Instant ativadaEm) {

  /** Anos completos em {@code hoje}; vazio enquanto o cadastro não gravou a data de nascimento. */
  public OptionalInt idadeEm(LocalDate hoje) {
    LocalDate nascimento = dataDeNascimento;
    if (nascimento == null) {
      return OptionalInt.empty();
    }
    return OptionalInt.of(Period.between(nascimento, hoje).getYears());
  }
}
