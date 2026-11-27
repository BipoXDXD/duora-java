package bipotheking.net.duora.nucleo;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.web.RelogioDeTeste;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeradorDeIdsTest {

  private static final Instant INICIO = Instant.parse("2026-11-22T12:00:00Z");
  private static final long MS_DO_INICIO = 1_795_348_800_000L;

  private final RelogioDeTeste relogio = new RelogioDeTeste(INICIO);
  private final GeradorDeIds gerador = new GeradorDeIds(relogio, new SplittableRandom(7));

  @Test
  void versaoEVariante() {
    UUID id = gerador.novo();

    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void milissegundosNosBitsAltos() {
    UUID id = gerador.novo();

    assertThat(milissegundo(id)).isEqualTo(MS_DO_INICIO);
  }

  @Test
  void mesmoMilissegundoEstritamenteCrescente() {
    UUID primeiro = gerador.novo();
    UUID segundo = gerador.novo();

    assertThat(compararSemSinal(segundo, primeiro)).isPositive();
  }

  @Test
  void estouroDaSequenciaAvancaOMilissegundo() {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 10_000; i++) {
      ids.add(gerador.novo());
    }

    assertThat(milissegundo(ids.get(4096))).isEqualTo(MS_DO_INICIO + 1);
    assertThat(new HashSet<>(ids)).hasSize(ids.size());
    for (int i = 1; i < ids.size(); i++) {
      assertThat(compararSemSinal(ids.get(i), ids.get(i - 1))).isPositive();
    }
  }

  @Test
  void relogioQueVoltaNaoQuebraAOrdem() {
    UUID anterior = gerador.novo();

    relogio.ajustar(INICIO.minusSeconds(5));
    UUID proximo = gerador.novo();

    assertThat(compararSemSinal(proximo, anterior)).isPositive();
  }

  @Test
  void mesmaSementeMesmosIds() {
    GeradorDeIds um = new GeradorDeIds(relogio, new SplittableRandom(42));
    GeradorDeIds outro = new GeradorDeIds(relogio, new SplittableRandom(42));

    List<UUID> doUm = List.of(um.novo(), um.novo(), um.novo());
    List<UUID> doOutro = List.of(outro.novo(), outro.novo(), outro.novo());

    assertThat(doUm).isEqualTo(doOutro);
  }

  private static long milissegundo(UUID id) {
    return id.getMostSignificantBits() >>> 16;
  }

  private static int compararSemSinal(UUID a, UUID b) {
    int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
    return msb != 0
        ? msb
        : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
  }
}
