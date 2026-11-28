package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ContaResumoTest {

  private static final String UUID_FIXO = "0196f1a0-0000-7000-8000-000000000001";
  private static final ContaId ID = new ContaId(UUID.fromString(UUID_FIXO));
  private static final LocalDate HOJE = LocalDate.of(2026, 5, 10);

  @ParameterizedTest
  @CsvSource({
    "2008-05-09, 2026-05-10, 18",
    "2008-05-10, 2026-05-10, 18",
    "2008-05-11, 2026-05-10, 17",
    "2008-02-29, 2026-02-28, 17",
    "2008-02-29, 2026-03-01, 18"
  })
  void idadeContaSoAniversariosCompletos(LocalDate nascimento, LocalDate hoje, int idade) {
    assertThat(resumo(nascimento).idadeEm(hoje)).hasValue(idade);
  }

  @Test
  void semNascimentoNaoTemIdade() {
    ContaResumo resumo = new ContaResumo(ID, EstadoConta.PENDENTE, null, false, null, false);

    assertThat(resumo.idadeEm(HOJE)).isEmpty();
  }

  @Test
  void hojeAntesDoNascimentoRecusado() {
    ContaResumo resumo = resumo(HOJE.plusDays(1));

    assertThatThrownBy(() -> resumo.idadeEm(HOJE)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void toStringNaoTrazADataDeNascimento() {
    ContaResumo resumo =
        new ContaResumo(
            ID,
            EstadoConta.ATIVA,
            LocalDate.of(1990, 5, 10),
            false,
            Instant.parse("2026-11-22T12:00:00Z"),
            false);

    assertThat(resumo.toString())
        .isEqualTo(
            "ContaResumo[id="
                + UUID_FIXO
                + ", estado=ATIVA, verificada=false,"
                + " ativadaEm=2026-11-22T12:00:00Z, deTeste=false]")
        .doesNotContain("1990");
  }

  @Test
  void contaIdImprimeSoOUuid() {
    assertThat(ID).hasToString(UUID_FIXO);
  }

  private static ContaResumo resumo(LocalDate nascimento) {
    return new ContaResumo(ID, EstadoConta.ATIVA, nascimento, false, null, false);
  }
}
