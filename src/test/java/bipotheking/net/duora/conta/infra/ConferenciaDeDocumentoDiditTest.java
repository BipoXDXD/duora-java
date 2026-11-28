package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.web.client.RestClient;

/**
 * Os dois passos da Didit contra o {@link ServidorDiditFalso}: o ID Verification (passo 1) e o Face
 * Match (passo 2), com o que vai no multipart, como cada resposta é lida, o que vira {@link
 * FornecedorIndisponivel}, os timeouts de cada passo, as métricas e o que nunca vai para o log.
 * Nada vai à Didit.
 */
@ExtendWith(OutputCaptureExtension.class)
class ConferenciaDeDocumentoDiditTest {

  private static final String CAMINHO = "/v3/id-verification/";
  private static final String CAMINHO_DO_ROSTO = "/v3/face-match/";
  private static final String CHAVE = "chave-de-teste";
  private static final Imagem FRENTE = jpeg("frente-de-teste");
  private static final Imagem VERSO = jpeg("verso-de-teste");
  private static final Imagem SELFIE = jpeg("selfie-de-teste");
  private static final byte[] BYTES_DO_RETRATO = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3};
  private static final String RETRATO = Base64.getEncoder().encodeToString(BYTES_DO_RETRATO);

  private final ServidorDiditFalso didit = ServidorDiditFalso.iniciar();
  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @AfterEach
  void fechar() {
    didit.close();
  }

  @Test
  void enviaSaveApiRequestFalse() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    ServidorDiditFalso.Requisicao requisicao = didit.requisicaoEm(CAMINHO);
    assertThat(requisicao.campo("save_api_request")).isEqualTo("false");
    assertThat(requisicao.campo("perform_document_liveness")).isEqualTo("true");
  }

  @Test
  void enviaChaveNoCabecalhoXApiKey() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    ServidorDiditFalso.Requisicao requisicao = didit.requisicaoEm(CAMINHO);
    assertThat(requisicao.cabecalho("x-api-key")).isEqualTo(CHAVE);
    assertThat(requisicao.caminho()).isEqualTo(CAMINHO);
    assertThat(requisicao.metodo()).isEqualTo("POST");
    assertThat(requisicao.cabecalho("Content-Type")).startsWith("multipart/form-data");
  }

  @Test
  void naoEnviaVendorData() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.requisicaoEm(CAMINHO).corpoComoTexto())
        .doesNotContain("vendor_data")
        .doesNotContain("metadata");
  }

  @Test
  void enviaFrenteEVerso() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.requisicaoEm(CAMINHO).corpoComoTexto())
        .contains("name=\"front_image\"; filename=\"frente\"")
        .contains("name=\"back_image\"; filename=\"verso\"")
        .contains("frente-de-teste")
        .contains("verso-de-teste")
        .doesNotContain("selfie-de-teste");
  }

  @Test
  void aprovadoLeData() {
    responderRosto("Approved");
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": "1990-05-10",
         "portrait_image": "%s", "full_name": "Fulana de Tal", "warnings": [],
         "document_type": "Identity Card"}, "request_id": "r-1"}
        """
            .formatted(RETRATO));

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado.documentoAceito()).isTrue();
    assertThat(resultado.dataDeNascimento()).contains(LocalDate.of(1990, 5, 10));
  }

  @Test
  void aprovadoComRostoConferido() {
    responderAprovado();

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado)
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(LocalDate.of(1990, 5, 10)), true));
  }

  @Test
  void faceMatchEnviaSelfieRetratoLimiarESaveFalse() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    ServidorDiditFalso.Requisicao rosto = didit.requisicaoEm(CAMINHO_DO_ROSTO);
    assertThat(rosto.metodo()).isEqualTo("POST");
    assertThat(rosto.cabecalho("x-api-key")).isEqualTo(CHAVE);
    assertThat(rosto.campo("face_match_score_decline_threshold")).isEqualTo("30.0");
    assertThat(rosto.campo("save_api_request")).isEqualTo("false");
    assertThat(rosto.arquivo("ref_image")).isEqualTo(BYTES_DO_RETRATO);
    assertThat(rosto.arquivo("user_image")).isEqualTo(SELFIE.bytes());
    assertThat(rosto.corpoComoTexto()).doesNotContain("vendor_data").doesNotContain("metadata");
  }

  @ParameterizedTest
  @ValueSource(strings = {"Approved", "Declined"})
  void faceMatchComStatusConhecidoNaoAvisa(String status, CapturedOutput saida) {
    responderAprovado();
    responderRosto(status);

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(saida.getOut()).doesNotContain("status desconhecido");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"face_match\": {\"status\": \"Talvez\", \"score\": 85.5}}",
        "{\"face_match\": {\"score\": 85.5}}",
        "{\"request_id\": \"r-2\"}"
      })
  void faceMatchSemStatusConhecidoNaoConfereEAvisa(String corpo, CapturedOutput saida) {
    responderAprovado();
    didit.responder(CAMINHO_DO_ROSTO, 200, corpo);

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).rostoConfere()).isFalse();
    assertThat(saida.getOut()).contains("status desconhecido em rosto");
  }

  @Test
  void faceMatchLentoViraIndisponivel() {
    responderAprovado();
    didit.responder(
        CAMINHO_DO_ROSTO,
        200,
        "{\"face_match\": {\"status\": \"Approved\"}}",
        Duration.ofSeconds(3));
    ConferenciaDeDocumentoDidit comRostoCurto =
        conferencia(Duration.ofSeconds(9), Duration.ofMillis(200));

    long inicio = System.nanoTime();
    FornecedorIndisponivel falha =
        catchThrowableOfType(
            FornecedorIndisponivel.class, () -> comRostoCurto.conferir(FRENTE, VERSO, SELFIE));
    Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

    assertThat(falha).isNotNull();
    assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
  }

  @Test
  void rostoAbaixoDoLimiarViraNaoConfere() {
    responderAprovado();
    responderRosto("Declined");

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).rostoConfere()).isFalse();
  }

  @Test
  void documentoRecusadoNaoChamaFaceMatch() {
    responderComStatus("Declined");
    responderRosto("Approved");

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.requisicoes())
        .extracting(ServidorDiditFalso.Requisicao::caminho)
        .containsExactly(CAMINHO);
  }

  @Test
  void semRetratoNaoChamaFaceMatch() {
    responderDocumento("\"1990-05-10\"", "null");
    responderRosto("Approved");

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado)
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(LocalDate.of(1990, 5, 10)), false));
    assertThat(didit.requisicoes())
        .extracting(ServidorDiditFalso.Requisicao::caminho)
        .containsExactly(CAMINHO);
  }

  @Test
  void semDataNaoChamaFaceMatch() {
    responderDocumento("null", "\"" + RETRATO + "\"");
    responderRosto("Approved");

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.requisicoes())
        .extracting(ServidorDiditFalso.Requisicao::caminho)
        .containsExactly(CAMINHO);
  }

  @Test
  void dataDeMenorAindaChamaFaceMatch() {
    responderDocumento("\"2015-03-01\"", "\"" + RETRATO + "\"");
    responderRosto("Approved");

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado.rostoConfere()).isTrue();
    assertThat(didit.requisicaoEm(CAMINHO_DO_ROSTO)).isNotNull();
  }

  @Test
  void faceMatch500ViraIndisponivelSemRepetir() {
    responderAprovado();
    didit.responder(CAMINHO_DO_ROSTO, 500, "{\"detail\": \"erro\"}");

    FornecedorIndisponivel falha = falhaDaConferencia();

    assertThat(falha.getCause()).isNull();
    assertThat(didit.requisicoes()).hasSize(2);
  }

  @Test
  void metricaSeparaDocumentoERosto() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(contagem("documento")).isEqualTo(1);
    assertThat(contagem("rosto")).isEqualTo(1);
  }

  @Test
  void documentoRecusado() {
    responderComStatus("Declined");

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).documentoAceito()).isFalse();
  }

  @Test
  void emAnaliseContaComoNaoAceito() {
    responderComStatus("In Review");

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).documentoAceito()).isFalse();
  }

  @Test
  void statusDesconhecidoNaoAceitaEAvisa(CapturedOutput saida) {
    responderComStatus("Talvez");

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).documentoAceito()).isFalse();
    assertThat(saida.getOut()).contains("status desconhecido");
  }

  @Test
  void semIdVerificationNaoAceitaEAvisa(CapturedOutput saida) {
    didit.responder(CAMINHO, 200, "{\"request_id\": \"r-1\"}");

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).documentoAceito()).isFalse();
    assertThat(saida.getOut()).contains("sem id_verification");
  }

  @Test
  void jsonQuebradoViraIndisponivel() {
    didit.responder(CAMINHO, 200, "{\"id_verification\": {\"status\": ");

    assertThat(falhaDaConferencia()).isNotNull();
  }

  @Test
  void dataInvalidaViraVazia() {
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": "10/05/1990",
         "portrait_image": "%s"}}
        """
            .formatted(RETRATO));

    assertThat(conferencia().conferir(FRENTE, VERSO, SELFIE).dataDeNascimento()).isEmpty();
  }

  @Test
  void retratoInvalidoAvisa(CapturedOutput saida) {
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": "1990-05-10",
         "portrait_image": "isto nao e base64!"}}
        """);

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(saida.getOut()).contains("retrato que não é JPEG nem PNG");
  }

  @Test
  void retratoEmBase64QueNaoEImagemAvisa(CapturedOutput saida) {
    String texto =
        Base64.getEncoder().encodeToString("nao-e-imagem".getBytes(StandardCharsets.UTF_8));
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": "1990-05-10",
         "portrait_image": "%s"}}
        """
            .formatted(texto));

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(saida.getOut()).contains("retrato que não é JPEG nem PNG");
  }

  @Test
  void naoReconhecidoViraNaoAceito(CapturedOutput saida) {
    didit.responder(CAMINHO, 400, "{\"error\": \"COULD_NOT_RECOGNIZE_DOCUMENT\"}");

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado).isEqualTo(new ResultadoDoDocumento(false, Optional.empty(), false));
    assertThat(saida.getOut()).contains("Didit não reconheceu o documento");
  }

  @Test
  void outro400ViraIndisponivel() {
    didit.responder(CAMINHO, 400, "{\"error\": \"INVALID_IMAGE\"}");

    assertThat(falhaDaConferencia()).isNotNull();
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403})
  void chaveInvalidaOuSemSaldoViraIndisponivel(int status, CapturedOutput saida) {
    didit.responder(
        CAMINHO, status, "{\"detail\": \"You do not have permission to perform this action.\"}");

    FornecedorIndisponivel falha = falhaDaConferencia();

    assertThat(falha.getCause()).isNull();
    assertThat(falha.getMessage()).doesNotContain(CHAVE);
    assertThat(saida.getOut()).contains("confira duora.verificacao.didit.api-key");
  }

  @Test
  void limiteDeTaxaViraIndisponivelComAviso(CapturedOutput saida) {
    didit.responder(CAMINHO, 429, "{\"detail\": \"Request was throttled.\"}");

    falhaDaConferencia();

    assertThat(saida.getOut()).contains("WARN").contains("Didit indisponível em documento (429)");
  }

  @Test
  void erro500NaoRepete() {
    didit.responder(CAMINHO, 500, "{\"detail\": \"erro\"}");

    falhaDaConferencia();

    assertThat(didit.requisicoes()).hasSize(1);
  }

  @Test
  void respostaLentaFalhaPeloTimeoutSemRepetir() {
    responderAprovadoDepois(Duration.ofSeconds(3));
    ConferenciaDeDocumentoDidit comLeituraCurta = conferencia(Duration.ofMillis(200));

    long inicio = System.nanoTime();
    FornecedorIndisponivel falha =
        catchThrowableOfType(
            FornecedorIndisponivel.class, () -> comLeituraCurta.conferir(FRENTE, VERSO, SELFIE));
    Duration decorrido = Duration.ofNanos(System.nanoTime() - inicio);

    assertThat(falha).isNotNull();
    assertThat(decorrido).isLessThan(Duration.ofSeconds(1));
    assertThat(didit.requisicoes()).hasSize(1);
  }

  @Test
  void logNaoTrazCorpoChaveNemImagem(CapturedOutput saida) {
    didit.responder(CAMINHO, 500, "{\"detail\": \"corpo-canario\"}");
    falhaDaConferencia();
    didit.responder(CAMINHO, 403, "{\"detail\": \"corpo-canario\"}");
    falhaDaConferencia();
    responderComStatus("corpo-canario");
    conferencia().conferir(FRENTE, VERSO, SELFIE);
    didit.responder(
        CAMINHO,
        400,
        "{\"error\": \"COULD_NOT_RECOGNIZE_DOCUMENT\", \"detail\": \"corpo-canario\"}");
    conferencia().conferir(FRENTE, VERSO, SELFIE);
    didit.responder(CAMINHO, 200, "{\"detail\": \"corpo-canario\"}", Duration.ofSeconds(2));
    ConferenciaDeDocumentoDidit comLeituraCurta = conferencia(Duration.ofMillis(200));
    catchThrowableOfType(
        FornecedorIndisponivel.class, () -> comLeituraCurta.conferir(FRENTE, VERSO, SELFIE));
    responderAprovado();
    didit.responder(CAMINHO_DO_ROSTO, 500, "{\"detail\": \"corpo-canario\"}");
    falhaDaConferencia();

    assertThat(saida.getOut())
        .doesNotContain("corpo-canario")
        .doesNotContain(CHAVE)
        .doesNotContain(Base64.getEncoder().encodeToString(FRENTE.bytes()))
        .doesNotContain("frente-de-teste")
        .doesNotContain(RETRATO)
        .doesNotContain("selfie-de-teste");
  }

  private long contagem(String operacao) {
    Timer timer =
        metricas
            .find("duora.verificacao.fornecedor")
            .tags("fornecedor", "didit", "operacao", operacao, "desfecho", "sucesso")
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private ConferenciaDeDocumentoDidit conferencia() {
    return conferencia(Duration.ofSeconds(9));
  }

  private ConferenciaDeDocumentoDidit conferencia(Duration leituraDoDocumento) {
    return conferencia(leituraDoDocumento, Duration.ofSeconds(4));
  }

  private ConferenciaDeDocumentoDidit conferencia(
      Duration leituraDoDocumento, Duration leituraDoRosto) {
    ConfiguracaoDidit config =
        new ConfiguracaoDidit(
            didit.uri(), CHAVE, 30, Duration.ofSeconds(1), leituraDoDocumento, leituraDoRosto);
    return ConferenciaDeDocumentoDidit.comTimeouts(
        RestClient.builder(), config, new MedicaoDeFornecedor(metricas));
  }

  private FornecedorIndisponivel falhaDaConferencia() {
    FornecedorIndisponivel falha =
        catchThrowableOfType(
            FornecedorIndisponivel.class, () -> conferencia().conferir(FRENTE, VERSO, SELFIE));
    assertThat(falha).isNotNull();
    return falha;
  }

  private void responderAprovado() {
    responderAprovadoDepois(Duration.ZERO);
  }

  private void responderRosto(String status) {
    didit.responder(
        CAMINHO_DO_ROSTO,
        200,
        """
        {"face_match": {"status": "%s", "score": 85.5, "warnings": []}, "request_id": "r-2"}
        """
            .formatted(status));
  }

  private void responderDocumento(String data, String retrato) {
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": %s, "portrait_image": %s}}
        """
            .formatted(data, retrato));
  }

  private void responderAprovadoDepois(Duration atraso) {
    responderRosto("Approved");
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "Approved", "date_of_birth": "1990-05-10",
         "portrait_image": "%s"}}
        """
            .formatted(RETRATO),
        atraso);
  }

  private void responderComStatus(String status) {
    didit.responder(
        CAMINHO,
        200,
        """
        {"id_verification": {"status": "%s", "date_of_birth": "1990-05-10"}}
        """
            .formatted(status));
  }

  private static Imagem jpeg(String marcador) {
    byte[] texto = marcador.getBytes(StandardCharsets.US_ASCII);
    byte[] bytes = new byte[3 + texto.length];
    bytes[0] = (byte) 0xFF;
    bytes[1] = (byte) 0xD8;
    bytes[2] = (byte) 0xFF;
    System.arraycopy(texto, 0, bytes, 3, texto.length);
    return Imagem.ler(bytes).orElseThrow();
  }
}
