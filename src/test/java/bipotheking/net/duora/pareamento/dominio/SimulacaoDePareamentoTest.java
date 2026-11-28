package bipotheking.net.duora.pareamento.dominio;

import static bipotheking.net.duora.pareamento.dominio.PerfisFicticios.AGORA;
import static bipotheking.net.duora.pareamento.dominio.PerfisFicticios.CICLO;
import static bipotheking.net.duora.pareamento.dominio.PerfisFicticios.pessoa;
import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.pareamento.Candidato;
import bipotheking.net.duora.pareamento.ParDeContas;
import bipotheking.net.duora.pareamento.ParPareado;
import bipotheking.net.duora.pareamento.ResultadoDoCiclo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * A prova da spec do pareamento: os 30 perfis de {@link PerfisFicticios} passam pelo {@link
 * Pareador} e o resultado tem de bater com a tabela escrita à mão no comentário daquela classe.
 */
class SimulacaoDePareamentoTest {

  private static final int VAGAS = 10;
  private static final Path RELATORIO = Path.of("target", "simulacao-pareamento.md");

  private static ResultadoDoCiclo parear(List<Candidato> fila, int vagas, Instant agora) {
    return Pareador.parear(fila, PerfisFicticios.vetados(), vagas, agora, CICLO);
  }

  private static ResultadoDoCiclo cicloDosTrinta() {
    return parear(PerfisFicticios.trinta(), VAGAS, AGORA);
  }

  private static ParDeContas par(int uma, int outra) {
    return ParDeContas.de(pessoa(uma), pessoa(outra));
  }

  private static Set<ParDeContas> paresDe(ResultadoDoCiclo resultado) {
    return resultado.pares().stream().map(ParPareado::par).collect(Collectors.toSet());
  }

  @Test
  void trintaPerfisGeramOsParesEsperados() {
    assertThat(paresDe(cicloDosTrinta()))
        .containsExactlyInAnyOrder(
            par(1, 29),
            par(2, 17),
            par(3, 10),
            par(4, 12),
            par(5, 25),
            par(7, 15),
            par(8, 22),
            par(13, 19),
            par(14, 20));
  }

  @Test
  void todoParECompativelNosDoisSentidos() {
    Map<ContaId, Candidato> porConta = porConta(PerfisFicticios.trinta());

    for (ParPareado par : cicloDosTrinta().pares()) {
      Candidato a = porConta.get(par.a());
      Candidato b = porConta.get(par.b());
      assertThat(Compatibilidade.compativeis(a, b)).as("%s", par).isTrue();
      assertThat(Compatibilidade.compativeis(b, a)).as("%s", par).isTrue();
      assertThat(par.intencoes())
          .as("%s", par)
          .isEqualTo(Compatibilidade.intencoesEmComum(a.preferencias(), b.preferencias()))
          .isEqualTo(Compatibilidade.intencoesEmComum(b.preferencias(), a.preferencias()));
    }
  }

  @Test
  void ninguemEmDoisPares() {
    ResultadoDoCiclo resultado = cicloDosTrinta();
    List<ContaId> todas = new ArrayList<>();
    resultado.pares().forEach(par -> todas.addAll(List.of(par.a(), par.b())));
    resultado.semPar().forEach(candidato -> todas.add(candidato.conta()));

    assertThat(todas)
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(
            PerfisFicticios.trinta().stream().map(Candidato::conta).toList());
  }

  @Test
  void nenhumParVetado() {
    assertThat(PerfisFicticios.vetados())
        .containsExactlyInAnyOrder(par(5, 13), par(5, 19), par(6, 14), par(6, 20), par(8, 16));

    assertThat(paresDe(cicloDosTrinta())).doesNotContainAnyElementsOf(PerfisFicticios.vetados());
  }

  @Test
  void maximalidade() {
    ResultadoDoCiclo resultado = cicloDosTrinta();
    List<Candidato> semPar = resultado.semPar();

    assertThat(resultado.pares()).hasSizeLessThan(VAGAS);
    for (int i = 0; i < semPar.size(); i++) {
      for (int j = i + 1; j < semPar.size(); j++) {
        assertThat(Compatibilidade.compativeis(semPar.get(i), semPar.get(j)))
            .as("%s e %s", semPar.get(i).conta(), semPar.get(j).conta())
            .isFalse();
      }
    }
  }

  @Test
  void contasDeTesteSoEntreSi() {
    Map<ContaId, Candidato> porConta = porConta(PerfisFicticios.trinta());

    assertThat(cicloDosTrinta().pares())
        .allSatisfy(par ->
            assertThat(porConta.get(par.a()).deTeste()).isEqualTo(porConta.get(par.b()).deTeste()))
        .filteredOn(par -> porConta.get(par.a()).deTeste())
        .extracting(ParPareado::par)
        .containsExactly(par(1, 29));
  }

  @Test
  void capacidadeCheiaNaoParea() {
    ResultadoDoCiclo resultado = parear(PerfisFicticios.trinta(), 0, AGORA);

    assertThat(resultado.pares()).isEmpty();
    assertThat(resultado.semPar()).hasSize(30);
  }

  @Test
  void ciclosSucessivosNaoInventamPares() {
    List<Candidato> fila = PerfisFicticios.trinta();
    Instant agora = AGORA;
    int primeiro = cicloDosTrinta().pares().size();
    int total = 0;

    for (int rodada = 0; rodada < 3; rodada++) {
      ResultadoDoCiclo resultado = parear(fila, VAGAS, agora);
      total += resultado.pares().size();
      fila = resultado.semPar();
      agora = agora.plus(CICLO);
    }

    assertThat(total).isEqualTo(primeiro);
  }

  @AfterAll
  static void gravarRelatorio() throws IOException {
    ResultadoDoCiclo resultado = cicloDosTrinta();
    StringBuilder md = new StringBuilder();
    md.append("# Simulação de pareamento: 30 perfis fictícios\n\n")
        .append("Um ciclo, %d vagas, %d vetos. ".formatted(VAGAS, PerfisFicticios.vetados().size()))
        .append(
            "%d pares, %d sem par.\n\n"
                .formatted(resultado.pares().size(), resultado.semPar().size()))
        .append("## Pares\n\n| A (espera há mais tempo) | B | Intenções |\n|---|---|---|\n");
    resultado
        .pares()
        .forEach(par ->
            md.append(
                "| %s | %s | %s |\n"
                    .formatted(
                        PerfisFicticios.rotulo(par.a()),
                        PerfisFicticios.rotulo(par.b()),
                        par.intencoes())));
    md.append("\n## Desfecho de cada pessoa\n\n| Pessoa | Desfecho |\n|---|---|\n");
    Stream.concat(
            resultado.pares().stream().flatMap(par -> Stream.of(par.a(), par.b())),
            resultado.semPar().stream().map(Candidato::conta))
        .sorted(
            (uma, outra) -> PerfisFicticios.rotulo(uma).compareTo(PerfisFicticios.rotulo(outra)))
        .forEach(conta ->
            md.append(
                "| %s | %s |\n"
                    .formatted(PerfisFicticios.rotulo(conta), PerfisFicticios.desfecho(conta))));
    Files.createDirectories(RELATORIO.getParent());
    Files.writeString(RELATORIO, md, StandardCharsets.UTF_8);
  }

  private static Map<ContaId, Candidato> porConta(List<Candidato> candidatos) {
    return candidatos.stream().collect(Collectors.toMap(Candidato::conta, Function.identity()));
  }
}
