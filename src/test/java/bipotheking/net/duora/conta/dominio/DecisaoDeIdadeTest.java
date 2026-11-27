package bipotheking.net.duora.conta.dominio;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class DecisaoDeIdadeTest {

  private static final Imagem REFERENCIA =
      Imagem.ler(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}).orElseThrow();
  private static final LocalDate HOJE = LocalDate.parse("2026-11-22");
  private static final LocalDate DECLARADA = LocalDate.parse("1990-05-10");

  /** Colunas {@code baixa}/{@code alta} vazias = prova sem faixa (nenhum rosto ou mais de um). */
  @ParameterizedTest(name = "{0}, confiança {1}, faixa {2}–{3} -> {4}")
  @CsvSource({
    "CONCLUIDA,    95.0, 21, 29, APROVADA",
    "CONCLUIDA,    95.0, 20, 28, DUVIDA",
    "CONCLUIDA,    95.0, 12, 17, RECUSADA",
    "CONCLUIDA,    95.0, 13, 18, DUVIDA",
    "CONCLUIDA,    90.0, 30, 40, APROVADA",
    "CONCLUIDA,    89.9, 30, 40, REPETIR",
    "CONCLUIDA,     NaN, 30, 40, REPETIR",
    "CONCLUIDA,    95.0,   ,   , REPETIR",
    "FALHOU,       95.0, 30, 40, REPETIR",
    "EXPIRADA,     95.0, 30, 40, REPETIR",
    "EM_ANDAMENTO, 95.0, 30, 40, REPETIR",
    "CRIADA,       95.0, 30, 40, REPETIR"
  })
  void pelaSelfie(
      SituacaoDaProva situacao,
      double confianca,
      @Nullable Integer baixa,
      @Nullable Integer alta,
      ResultadoDaVerificacao esperado) {
    Optional<FaixaEstimada> faixa =
        baixa == null || alta == null
            ? Optional.empty()
            : Optional.of(new FaixaEstimada(baixa, alta));
    ResultadoDaProva prova =
        new ResultadoDaProva(situacao, confianca, faixa, Optional.of(REFERENCIA));

    assertThat(DecisaoDeIdade.pelaSelfie(prova)).isEqualTo(esperado);
  }

  @Test
  void provaConcluidaConfiavelComFaixaEReferenciaServeParaDocumento() {
    ResultadoDaProva prova = prova(SituacaoDaProva.CONCLUIDA, 95.0, true, true);

    assertThat(DecisaoDeIdade.provaServeParaDocumento(prova)).isTrue();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provasQueNaoServemParaDocumento")
  void provaNaoServeParaDocumento(String caso, ResultadoDaProva prova) {
    assertThat(DecisaoDeIdade.provaServeParaDocumento(prova)).isFalse();
  }

  static Stream<Arguments> provasQueNaoServemParaDocumento() {
    return Stream.of(
        Arguments.of("confiança 89,9", prova(SituacaoDaProva.CONCLUIDA, 89.9, true, true)),
        Arguments.of("sem referência", prova(SituacaoDaProva.CONCLUIDA, 95.0, true, false)),
        Arguments.of("sem faixa", prova(SituacaoDaProva.CONCLUIDA, 95.0, false, true)),
        Arguments.of("expirada", prova(SituacaoDaProva.EXPIRADA, 95.0, true, true)));
  }

  @ParameterizedTest(name = "aceito {0}, lida {1}, rosto {2} -> {3}")
  @CsvSource({
    "true,  1990-05-10, true,  APROVADA",
    "true,  1990-05-11, true,  DUVIDA",
    "true,  2008-11-23, true,  RECUSADA",
    "true,  2008-11-23, false, DUVIDA",
    "false, 1990-05-10, true,  REPETIR",
    "true,            , true,  REPETIR"
  })
  void peloDocumento(
      boolean aceito,
      @Nullable LocalDate lida,
      boolean rostoConfere,
      ResultadoDaVerificacao esperado) {
    ResultadoDoDocumento documento =
        new ResultadoDoDocumento(aceito, Optional.ofNullable(lida), rostoConfere);

    assertThat(DecisaoDeIdade.peloDocumento(DECLARADA, documento, HOJE)).isEqualTo(esperado);
  }

  @Test
  void documentoDeQuemFazDezoitoHojeAprova() {
    LocalDate declarada = LocalDate.parse("2008-11-22");
    ResultadoDoDocumento documento = new ResultadoDoDocumento(true, Optional.of(declarada), true);

    assertThat(DecisaoDeIdade.peloDocumento(declarada, documento, HOJE))
        .isEqualTo(ResultadoDaVerificacao.APROVADA);
  }

  @Test
  void toStringEscondeAData() {
    ResultadoDoDocumento documento = new ResultadoDoDocumento(true, Optional.of(DECLARADA), true);

    assertThat(documento.toString()).doesNotContain("1990").contains("dataDeNascimento=***");
  }

  private static ResultadoDaProva prova(
      SituacaoDaProva situacao, double confianca, boolean comFaixa, boolean comReferencia) {
    return new ResultadoDaProva(
        situacao,
        confianca,
        comFaixa ? Optional.of(new FaixaEstimada(17, 24)) : Optional.empty(),
        comReferencia ? Optional.of(REFERENCIA) : Optional.empty());
  }
}
