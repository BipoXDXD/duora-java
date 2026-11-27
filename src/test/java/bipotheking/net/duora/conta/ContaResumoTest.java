package bipotheking.net.duora.conta;

import static org.assertj.core.api.Assertions.assertThat;

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
  @CsvSource({"2008-05-09, 18", "2008-05-10, 18", "2008-05-11, 17"})
  void idadeContaSoAniversariosCompletos(LocalDate nascimento, int idade) {
    ContaResumo resumo = new ContaResumo(ID, EstadoConta.ATIVA, nascimento, false, null);

    assertThat(resumo.idadeEm(HOJE)).hasValue(idade);
  }

  @Test
  void semNascimentoNaoTemIdade() {
    ContaResumo resumo = new ContaResumo(ID, EstadoConta.PENDENTE, null, false, null);

    assertThat(resumo.idadeEm(HOJE)).isEmpty();
  }

  @Test
  void contaIdImprimeSoOUuid() {
    assertThat(ID).hasToString(UUID_FIXO);
  }
}
