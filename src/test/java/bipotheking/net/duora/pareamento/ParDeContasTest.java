package bipotheking.net.duora.pareamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContaId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ParDeContasTest {

  private static final ContaId MENOR_NO_POSTGRES =
      new ContaId(UUID.fromString("00000000-0000-7000-7fff-ffffffffffff"));
  private static final ContaId MAIOR_NO_POSTGRES =
      new ContaId(UUID.fromString("00000000-0000-7000-8000-000000000000"));

  @Test
  void ordenaComoOPostgres() {
    ParDeContas par = ParDeContas.de(MAIOR_NO_POSTGRES, MENOR_NO_POSTGRES);

    assertThat(par.menor()).isEqualTo(MENOR_NO_POSTGRES);
    assertThat(par.maior()).isEqualTo(MAIOR_NO_POSTGRES);
  }

  @Test
  void mesmaOrdemNosDoisSentidos() {
    assertThat(ParDeContas.de(MENOR_NO_POSTGRES, MAIOR_NO_POSTGRES))
        .isEqualTo(ParDeContas.de(MAIOR_NO_POSTGRES, MENOR_NO_POSTGRES));
  }

  @Test
  void contaRepetidaFalha() {
    assertThatThrownBy(() -> ParDeContas.de(MENOR_NO_POSTGRES, MENOR_NO_POSTGRES))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void outroDevolveOParceiro() {
    ParDeContas par = ParDeContas.de(MENOR_NO_POSTGRES, MAIOR_NO_POSTGRES);

    assertThat(par.outro(MENOR_NO_POSTGRES)).isEqualTo(MAIOR_NO_POSTGRES);
    assertThat(par.outro(MAIOR_NO_POSTGRES)).isEqualTo(MENOR_NO_POSTGRES);
  }
}
