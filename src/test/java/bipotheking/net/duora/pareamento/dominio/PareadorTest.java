package bipotheking.net.duora.pareamento.dominio;

import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.T0;
import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.candidato;
import static bipotheking.net.duora.pareamento.dominio.CandidatoBuilder.conta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.pareamento.ParDeContas;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.pareamento.ResultadoDoCiclo;
import bipotheking.net.duora.perfil.Intencao;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class PareadorTest {

  private static final Duration CICLO = Duration.ofSeconds(5);
  private static final Instant AGORA = T0.plusSeconds(30);
  private static final int SEM_LIMITE_DE_VAGAS = 100;

  private static ResultadoDoCiclo parear(List<Candidato> fila) {
    return parear(fila, Set.of(), SEM_LIMITE_DE_VAGAS);
  }

  private static ResultadoDoCiclo parear(
      List<Candidato> fila, Set<ParDeContas> vetados, int vagas) {
    return Pareador.parear(fila, vetados, vagas, AGORA, CICLO);
  }

  private static ParPareado amizade(long a, long b) {
    return new ParPareado(conta(a), conta(b), Set.of(Intencao.AMIZADE));
  }

  private static List<Candidato> compativeis(int quantos) {
    return LongStream.rangeClosed(1, quantos)
        .mapToObj(n -> candidato(n).entrouSegundosDepois(n - 1).build())
        .toList();
  }

  @Test
  void filaVaziaNaoGeraPares() {
    assertThat(parear(List.of())).isEqualTo(new ResultadoDoCiclo(List.of(), List.of()));
  }

  @Test
  void umaPessoaFicaSemPar() {
    Candidato sozinha = candidato(1).build();

    assertThat(parear(List.of(sozinha)))
        .isEqualTo(new ResultadoDoCiclo(List.of(), List.of(sozinha)));
  }

  @Test
  void doisCompativeisFormamUmPar() {
    Candidato depois = candidato(1).entrouSegundosDepois(3).build();
    Candidato antes = candidato(2).build();

    assertThat(parear(List.of(depois, antes)).pares()).containsExactly(amizade(2, 1));
  }

  @Test
  void parVetadoNaoEPareado() {
    List<Candidato> fila = compativeis(2);

    ResultadoDoCiclo resultado =
        parear(fila, Set.of(ParDeContas.de(conta(1), conta(2))), SEM_LIMITE_DE_VAGAS);

    assertThat(resultado.pares()).isEmpty();
    assertThat(resultado.semPar()).containsExactlyElementsOf(fila);
  }

  @Test
  void vagasLimitamOsPares() {
    List<Candidato> fila = compativeis(6);

    ResultadoDoCiclo resultado = parear(fila, Set.of(), 2);

    assertThat(resultado.pares()).containsExactly(amizade(1, 2), amizade(3, 4));
    assertThat(resultado.semPar()).containsExactly(fila.get(4), fila.get(5));
  }

  @Test
  void vagaZeroNaoParea() {
    List<Candidato> fila = compativeis(2);

    ResultadoDoCiclo resultado = parear(fila, Set.of(), 0);

    assertThat(resultado.pares()).isEmpty();
    assertThat(resultado.semPar()).containsExactlyElementsOf(fila);
  }

  @Test
  void vagaNegativaFalha() {
    assertThatThrownBy(() -> parear(compativeis(2), Set.of(), -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cicloNaoPositivoFalha() {
    assertThatThrownBy(() -> Pareador.parear(compativeis(2), Set.of(), 1, AGORA, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void quemEsperaMaisEscolhePrimeiro() {
    Candidato maisVelha = candidato(1).idade(40).faixa(35, 99).build();
    Candidato jovem = candidato(2).idade(28).entrouSegundosDepois(1).build();
    Candidato disputada = candidato(3).idade(40).entrouSegundosDepois(2).build();

    ResultadoDoCiclo resultado = parear(List.of(jovem, disputada, maisVelha));

    assertThat(resultado.pares()).containsExactly(amizade(1, 3));
    assertThat(resultado.semPar()).containsExactly(jovem);
  }

  @Test
  void parceiroQueEsperaMaisCiclosVenceOMaisPerto() {
    Candidato primeira = candidato(1).celula("6gyf4").build();
    Candidato longeHaMaisTempo = candidato(2).celula("6gysh").entrouSegundosDepois(1).build();
    Candidato pertoHaPouco = candidato(3).celula("6gycf").entrouSegundosDepois(20).build();

    assertThat(parear(List.of(primeira, longeHaMaisTempo, pertoHaPouco)).pares())
        .containsExactly(amizade(1, 2));
  }

  @Test
  void empateNoMesmoCicloVaiParaOMaisPerto() {
    Candidato a = candidato(1).celula("6gyf4").build();
    Candidato b = candidato(2).celula("6gysh").entrouSegundosDepois(6).build();
    Candidato c = candidato(3).celula("6gycf").entrouSegundosDepois(6).build();

    assertThat(parear(List.of(a, b, c)).pares()).containsExactly(amizade(1, 3));
  }

  @Test
  void distanciaDesconhecidaFicaPorUltimo() {
    Candidato a = candidato(1).celula("6gyf4").build();
    Candidato semCelula = candidato(2).entrouSegundosDepois(6).build();
    Candidato longe = candidato(3).celula("6gysh").entrouSegundosDepois(6).build();

    assertThat(parear(List.of(a, semCelula, longe)).pares()).containsExactly(amizade(1, 3));
  }

  @Test
  void ninguemEmDoisPares() {
    ResultadoDoCiclo resultado = parear(compativeis(7));

    List<ContaId> pareadas =
        resultado.pares().stream().flatMap(par -> Stream.of(par.a(), par.b())).toList();
    assertThat(pareadas).hasSize(6).doesNotHaveDuplicates();
    assertThat(resultado.semPar()).hasSize(1);
  }

  @Test
  void resultadoNaoDependeDaOrdemDaLista() {
    List<Candidato> fila =
        List.of(
            candidato(1).celula("6gyf4").build(),
            candidato(2).celula("6gysh").entrouSegundosDepois(6).build(),
            candidato(3).celula("6gycf").entrouSegundosDepois(6).build(),
            candidato(4).entrouSegundosDepois(6).build(),
            candidato(5).idade(40).faixa(35, 99).entrouSegundosDepois(9).build(),
            candidato(6).idade(45).entrouSegundosDepois(12).build(),
            candidato(7).deTeste().entrouSegundosDepois(12).build(),
            candidato(8).deTeste().celula("6gyft").entrouSegundosDepois(20).build(),
            candidato(9).entrouSegundosDepois(25).build());
    ResultadoDoCiclo esperado = parear(fila);
    SplittableRandom aleatorio = new SplittableRandom(7);

    for (int i = 0; i < 20; i++) {
      List<Candidato> embaralhada = new ArrayList<>(fila);
      Collections.shuffle(embaralhada, aleatorio);
      assertThat(parear(embaralhada)).isEqualTo(esperado);
    }
  }
}
