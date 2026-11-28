package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerfilCompletoTest {

  @Test
  void aceitaIntencaoVaziaQueNaoSejaEnumSet() {
    PerfilCompleto perfil =
        new PerfilCompleto(
            new ContaId(UUID.randomUUID()),
            "Lia",
            30,
            Set.of(),
            "",
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            false);

    assertThat(perfil.intencao()).isEmpty();
  }
}
