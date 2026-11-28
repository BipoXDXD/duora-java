package bipotheking.net.duora.pareamento;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.CelulaGeohash;
import bipotheking.net.duora.perfil.FaixaEtaria;
import bipotheking.net.duora.perfil.Genero;
import bipotheking.net.duora.perfil.Intencao;
import bipotheking.net.duora.perfil.PerfilDeFila;
import bipotheking.net.duora.perfil.Preferencias;
import bipotheking.net.duora.perfil.Raio;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CandidatoTest {

  @Test
  void candidatoDoPerfilDeFilaCopiaOsCampos() {
    ContaId conta = new ContaId(new UUID(0L, 7L));
    Instant entrouEm = Instant.parse("2026-11-28T20:00:00Z");
    Preferencias preferencias =
        new Preferencias(
            EnumSet.of(Intencao.NAMORO),
            new FaixaEtaria(25, 40),
            Raio.ATE_20_KM,
            true,
            Optional.of(Genero.MULHER),
            EnumSet.of(Genero.HOMEM));
    Optional<CelulaGeohash> celula = Optional.of(new CelulaGeohash("6gyf4"));

    Candidato candidato =
        Candidato.de(conta, entrouEm, new PerfilDeFila(31, preferencias, celula, true));

    assertThat(candidato).isEqualTo(new Candidato(conta, entrouEm, 31, preferencias, celula, true));
  }
}
