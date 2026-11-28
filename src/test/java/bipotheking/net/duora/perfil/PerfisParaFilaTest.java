package bipotheking.net.duora.perfil;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.web.BancoDeTeste;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** A leitura em lote dos candidatos da fila, com o relógio em 2026-11-22T12:00Z. */
class PerfisParaFilaTest extends BancoDeTeste {

  private static final LocalDate NASCIMENTO = LocalDate.of(1990, 5, 10);
  private static final CelulaGeohash CELULA = new CelulaGeohash("6gyf4");
  private static final Preferencias SO_AMIZADE = preferencias(Intencao.AMIZADE);

  @Autowired private Perfis perfis;
  @Autowired private ContasDeTeste contas;
  @Autowired private PerfisDeTeste perfisDeTeste;
  @Autowired private PausaDaConta pausa;

  @Test
  void soProntasEntram() {
    ContaId pronta = contas.criar("Ana", NASCIMENTO).id();
    ContaId pausada = contas.criar("Bia", NASCIMENTO).id();
    pausa.pausar(pausada);
    ContaId semPerfil = contas.criarVerificada().id();
    ContaId namoroSemDados = contas.criarVerificada().id();
    perfisDeTeste.definir(namoroSemDados, "Cris", preferencias(Intencao.NAMORO), Optional.empty());
    ContaId suspensa = contas.criar(EstadoConta.SUSPENSA, NASCIMENTO).id();
    perfisDeTeste.definir(suspensa, "Dani", SO_AMIZADE, Optional.empty());

    Map<ContaId, PerfilDeFila> fila =
        perfis.paraFila(List.of(pronta, pausada, semPerfil, namoroSemDados, suspensa));

    assertThat(fila).containsOnlyKeys(pronta);
  }

  @Test
  void idadeNoFusoDeSaoPaulo() {
    relogio.ajustar(Instant.parse("2026-11-22T02:00:00Z"));
    ContaId conta = contas.criar("Ana", LocalDate.of(2000, 11, 22)).id();

    assertThat(perfis.paraFila(List.of(conta)).get(conta).idade()).isEqualTo(25);
  }

  @Test
  void preferenciasECelulaIguaisAsDaLeituraUnitaria() {
    ContaId conta = contas.criarVerificada().id();
    perfisDeTeste.definir(
        conta,
        "Cris",
        new Preferencias(
            EnumSet.of(Intencao.AMIZADE, Intencao.NAMORO),
            new FaixaEtaria(30, 45),
            Raio.ATE_50_KM,
            true,
            Optional.of(Genero.OUTRO),
            EnumSet.of(Genero.MULHER, Genero.PESSOA_NAO_BINARIA)),
        Optional.of(CELULA));

    PerfilDeFila naFila = perfis.paraFila(List.of(conta)).get(conta);

    assertThat(naFila.preferencias()).isEqualTo(perfis.preferencias(conta).orElseThrow());
    assertThat(naFila.celula()).isEqualTo(perfis.celula(conta)).contains(CELULA);
  }

  @Test
  void contaDeTesteVemMarcada() {
    ContaId conta = contas.criar("Ana", NASCIMENTO).id();

    assertThat(perfis.paraFila(List.of(conta)).get(conta).deTeste()).isTrue();
  }

  @Test
  void contaRealNaoVemMarcada() {
    ContaId conta = contas.criarReal("Ana", NASCIMENTO).id();

    assertThat(perfis.paraFila(List.of(conta)).get(conta).deTeste()).isFalse();
  }

  @Test
  void listaVaziaDevolveVazio() {
    assertThat(perfis.paraFila(List.of())).isEmpty();
  }

  @Test
  void duzentasContasNumaChamada() {
    List<ContaId> ids = new ArrayList<>();
    for (int i = 0; i < 200; i++) {
      ids.add(contas.criar("Conta" + i, NASCIMENTO).id());
    }

    assertThat(perfis.paraFila(ids)).hasSize(200);
  }

  @Test
  void toStringSemPreferenciasNemCelula() {
    PerfilDeFila perfil = new PerfilDeFila(36, SO_AMIZADE, Optional.of(CELULA), true);

    assertThat(perfil.toString())
        .isEqualTo("PerfilDeFila[idade=36, deTeste=true, ***]")
        .doesNotContain("6gyf4")
        .doesNotContain("AMIZADE");
  }

  private static Preferencias preferencias(Intencao intencao) {
    return new Preferencias(
        EnumSet.of(intencao),
        FaixaEtaria.TODAS,
        Raio.SEM_LIMITE,
        false,
        Optional.empty(),
        Set.of());
  }
}
