package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
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

/** Passo 1 da Didit (ID Verification) contra o {@link ServidorDiditFalso}; nada vai à Didit. */
@ExtendWith(OutputCaptureExtension.class)
class ConferenciaDeDocumentoDiditTest {

  private static final String CAMINHO = "/v3/id-verification/";
  private static final String CHAVE = "chave-de-teste";
  private static final Imagem FRENTE = jpeg("frente-de-teste");
  private static final Imagem VERSO = jpeg("verso-de-teste");
  private static final Imagem SELFIE = jpeg("selfie-de-teste");
  private static final String RETRATO =
      Base64.getEncoder()
          .encodeToString(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3});

  private final ServidorDiditFalso didit = ServidorDiditFalso.iniciar();

  @AfterEach
  void fechar() {
    didit.close();
  }

  @Test
  void enviaSaveApiRequestFalse() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    ServidorDiditFalso.Requisicao requisicao = didit.unicaRequisicao();
    assertThat(requisicao.campo("save_api_request")).isEqualTo("false");
    assertThat(requisicao.campo("perform_document_liveness")).isEqualTo("true");
  }

  @Test
  void enviaChaveNoCabecalhoXApiKey() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    ServidorDiditFalso.Requisicao requisicao = didit.unicaRequisicao();
    assertThat(requisicao.cabecalho("x-api-key")).isEqualTo(CHAVE);
    assertThat(requisicao.caminho()).isEqualTo(CAMINHO);
    assertThat(requisicao.metodo()).isEqualTo("POST");
    assertThat(requisicao.cabecalho("Content-Type")).startsWith("multipart/form-data");
  }

  @Test
  void naoEnviaVendorData() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.unicaRequisicao().corpoComoTexto())
        .doesNotContain("vendor_data")
        .doesNotContain("metadata");
  }

  @Test
  void enviaFrenteEVerso() {
    responderAprovado();

    conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(didit.unicaRequisicao().corpoComoTexto())
        .contains("name=\"front_image\"; filename=\"frente\"")
        .contains("name=\"back_image\"; filename=\"verso\"")
        .contains("frente-de-teste")
        .contains("verso-de-teste")
        .doesNotContain("selfie-de-teste");
  }

  @Test
  void aprovadoLeData() {
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

    assertThat(resultado)
        .isEqualTo(new ResultadoDoDocumento(true, Optional.of(LocalDate.of(1990, 5, 10)), false));
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
    assertThat(saida.getOut()).contains("WARN");
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

    assertThat(saida.getOut()).contains("WARN").contains("retrato");
  }

  @Test
  void naoReconhecidoViraNaoAceito(CapturedOutput saida) {
    didit.responder(CAMINHO, 400, "{\"error\": \"COULD_NOT_RECOGNIZE_DOCUMENT\"}");

    ResultadoDoDocumento resultado = conferencia().conferir(FRENTE, VERSO, SELFIE);

    assertThat(resultado).isEqualTo(new ResultadoDoDocumento(false, Optional.empty(), false));
    assertThat(saida.getOut()).contains("INFO");
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
    assertThat(saida.getOut()).contains("ERROR");
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

    assertThat(saida.getOut())
        .doesNotContain("corpo-canario")
        .doesNotContain(CHAVE)
        .doesNotContain(Base64.getEncoder().encodeToString(FRENTE.bytes()))
        .doesNotContain("frente-de-teste");
  }

  private ConferenciaDeDocumentoDidit conferencia() {
    return conferencia(Duration.ofSeconds(9));
  }

  private ConferenciaDeDocumentoDidit conferencia(Duration leituraDoDocumento) {
    ConfiguracaoDidit config =
        new ConfiguracaoDidit(
            didit.uri(),
            CHAVE,
            30,
            Duration.ofSeconds(1),
            leituraDoDocumento,
            Duration.ofSeconds(4));
    return ConferenciaDeDocumentoDidit.comTimeouts(
        RestClient.builder(), config, new MedicaoDeFornecedor(new SimpleMeterRegistry()));
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

  private void responderAprovadoDepois(Duration atraso) {
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
