package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A regra desta etapa: a titular vê a nítida, as outras contas a borrada, e só se ela está ativa.
 */
class AcessoAFotoPadraoTest {

  private final Map<ContaId, ContaResumo> contas = new HashMap<>();
  private final AcessoAFoto acesso =
      new AcessoAFotoPadrao(id -> Optional.ofNullable(contas.get(id)));

  @Test
  void titularVeNitida() {
    ContaId titular = conta(EstadoConta.ATIVA);

    assertThat(acesso.nivel(titular, titular)).isEqualTo(NivelDeAcesso.NITIDA);
  }

  @Test
  void outraContaVeBorrada() {
    ContaId titular = conta(EstadoConta.ATIVA);
    ContaId outra = conta(EstadoConta.ATIVA);

    assertThat(acesso.nivel(outra, titular)).isEqualTo(NivelDeAcesso.BORRADA);
  }

  @Test
  void titularSuspensoNinguemVe() {
    ContaId titular = conta(EstadoConta.SUSPENSA);
    ContaId outra = conta(EstadoConta.ATIVA);

    assertThat(acesso.nivel(outra, titular)).isEqualTo(NivelDeAcesso.NENHUM);
    assertThat(acesso.nivel(titular, titular)).isEqualTo(NivelDeAcesso.NENHUM);
  }

  @Test
  void titularExcluidoNinguemVe() {
    ContaId titular = conta(EstadoConta.EXCLUIDA);
    ContaId outra = conta(EstadoConta.ATIVA);

    assertThat(acesso.nivel(outra, titular)).isEqualTo(NivelDeAcesso.NENHUM);
    assertThat(acesso.nivel(titular, titular)).isEqualTo(NivelDeAcesso.NENHUM);
  }

  private ContaId conta(EstadoConta estado) {
    ContaId id = new ContaId(UUID.randomUUID());
    contas.put(
        id,
        new ContaResumo(
            id,
            estado,
            LocalDate.of(1990, 5, 10),
            true,
            Instant.parse("2026-11-22T12:00:00Z"),
            false));
    return id;
  }
}
