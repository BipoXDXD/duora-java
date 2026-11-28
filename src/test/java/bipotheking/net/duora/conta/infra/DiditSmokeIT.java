package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Fala com a Didit de verdade (sandbox), com as mesmas peças da produção: o {@code
 * RestClient.Builder} do Boot com o Jackson estrito e {@link ConferenciaDeDocumentoDidit}. É
 * manual: só roda com {@code DUORA_DIDIT_API_KEY}, {@code DUORA_SMOKE_DOCUMENTO} e {@code
 * DUORA_SMOKE_ROSTO} (caminhos de JPEG ou PNG fora do repositório) e é pulado sem elas, inclusive
 * na CI. O log traz só latência, status HTTP e códigos; nunca a chave nem o corpo das respostas.
 */
@EnabledIfEnvironmentVariable(named = "DUORA_DIDIT_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DUORA_SMOKE_DOCUMENTO", matches = ".+")
@EnabledIfEnvironmentVariable(named = "DUORA_SMOKE_ROSTO", matches = ".+")
class DiditSmokeIT {

  private static final Logger LOG = LoggerFactory.getLogger(DiditSmokeIT.class);
  private static final URI URL = URI.create("https://verification.didit.me");
  private static final Pattern CODIGO_DE_ERRO = Pattern.compile("[A-Z_]{1,60}");
  private static final Set<String> STATUS_DO_ROSTO = Set.of("Approved", "Declined", "In Review");

  /** Erros que a Didit documenta em texto (o Face Search devolve este para imagem sem rosto). */
  private static final Set<String> ERROS_EM_TEXTO = Set.of("No face detected in the image");

  @Test
  void sandboxAceitaFormatoEChave() throws IOException {
    Imagem documento = imagem("DUORA_SMOKE_DOCUMENTO");
    Imagem rosto = imagem("DUORA_SMOKE_ROSTO");

    ResultadoDoDocumento resultado =
        comBuilderDoBoot(builder -> {
          ConfiguracaoDidit config =
              new ConfiguracaoDidit(
                  URL,
                  System.getenv("DUORA_DIDIT_API_KEY"),
                  30,
                  Duration.ofSeconds(1),
                  Duration.ofSeconds(9),
                  Duration.ofSeconds(4));
          config.exigirCompleta();
          ConferenciaDeDocumentoDidit conferencia =
              ConferenciaDeDocumentoDidit.comTimeouts(
                  builder, config, new MedicaoDeFornecedor(new SimpleMeterRegistry()));
          long inicio = System.nanoTime();
          ResultadoDoDocumento conferido = conferencia.conferir(documento, documento, rosto);
          LOG.info(
              "Didit conferiu em {} ms (documento aceito: {}, rosto confere: {})",
              Duration.ofNanos(System.nanoTime() - inicio).toMillis(),
              conferido.documentoAceito(),
              conferido.rostoConfere());
          return conferido;
        });

    assertThat(resultado.documentoAceito()).as("documento aceito pelo sandbox").isTrue();
  }

  /**
   * Responde a dúvida da A9: com um retrato sem rosto, o Face Match responde 200 {@code Declined}
   * ou 4xx? Registra o status HTTP, o {@code status} e o código de erro sem afirmar o resultado; só
   * afirma que a chave foi aceita (nem 401 nem 403).
   */
  @Test
  void faceMatchComRetratoSemRostoRegistraAResposta() throws IOException {
    Imagem rosto = imagem("DUORA_SMOKE_ROSTO");
    byte[] semRosto = cinzaLiso();

    int status =
        comBuilderDoBoot(builder -> {
          MultipartBodyBuilder partes = new MultipartBodyBuilder();
          partes
              .part(
                  ConferenciaDeDocumentoDidit.CAMPO_DA_SELFIE,
                  rosto.bytes(),
                  ConferenciaDeDocumentoDidit.tipo(rosto))
              .filename("selfie");
          partes
              .part(ConferenciaDeDocumentoDidit.CAMPO_DO_RETRATO, semRosto, MediaType.IMAGE_JPEG)
              .filename("retrato");
          partes.part(ConferenciaDeDocumentoDidit.CAMPO_SALVAR_PEDIDO, "false");
          RestClient cliente =
              builder
                  .clone()
                  .baseUrl(URL.toString())
                  .defaultHeader(
                      ConferenciaDeDocumentoDidit.CABECALHO_DA_CHAVE,
                      variavel("DUORA_DIDIT_API_KEY"))
                  .build();
          return cliente
              .post()
              .uri(ConferenciaDeDocumentoDidit.CAMINHO_DO_ROSTO)
              .contentType(MediaType.MULTIPART_FORM_DATA)
              .body(partes.build())
              .exchangeForRequiredValue((pedido, resposta) -> {
                int codigoHttp = resposta.getStatusCode().value();
                registrar(codigoHttp, resposta.getBody().readAllBytes());
                return codigoHttp;
              });
        });

    assertThat(status).as("status HTTP do Face Match (chave aceita)").isNotIn(401, 403);
  }

  /** Só status, {@code face_match.status} e {@code error} conhecidos; nunca o corpo. */
  private static void registrar(int codigoHttp, byte[] corpo) {
    JsonNode json;
    try {
      json = JsonMapper.builder().build().readTree(corpo);
    } catch (JacksonException e) {
      LOG.info("Face Match com retrato sem rosto: HTTP {}, corpo não JSON", codigoHttp);
      return;
    }
    LOG.info(
        "Face Match com retrato sem rosto: HTTP {}, status {}, erro {}",
        codigoHttp,
        conhecido(json.path("face_match").path("status"), STATUS_DO_ROSTO::contains),
        conhecido(json.path("error"), DiditSmokeIT::erroConhecido));
  }

  private static boolean erroConhecido(String erro) {
    return CODIGO_DE_ERRO.matcher(erro).matches() || ERROS_EM_TEXTO.contains(erro);
  }

  private static <T> T comBuilderDoBoot(Function<RestClient.Builder, T> uso) {
    AtomicReference<T> resultado = new AtomicReference<>();
    TurnstileComBuilderDoBootTest.CONTEXTO.run(
        contexto -> resultado.set(uso.apply(contexto.getBean(RestClient.Builder.class))));
    return Objects.requireNonNull(resultado.get());
  }

  /** O valor só sai no log se for conhecido; o resto vira "(omitido)". */
  private static String conhecido(JsonNode valor, Predicate<String> aceito) {
    if (valor.isMissingNode() || valor.isNull()) {
      return "(ausente)";
    }
    String texto = valor.asString();
    return aceito.test(texto) ? texto : "(omitido)";
  }

  /** JPEG cinza liso, gerado em memória: não há rosto nele e nada vai para o repositório. */
  private static byte[] cinzaLiso() throws IOException {
    BufferedImage imagem = new BufferedImage(480, 640, BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < imagem.getWidth(); x++) {
      for (int y = 0; y < imagem.getHeight(); y++) {
        imagem.setRGB(x, y, 0x808080);
      }
    }
    ByteArrayOutputStream saida = new ByteArrayOutputStream();
    ImageIO.write(imagem, "jpg", saida);
    return saida.toByteArray();
  }

  private static Imagem imagem(String variavel) throws IOException {
    byte[] bytes = Files.readAllBytes(Path.of(variavel(variavel)));
    return Imagem.ler(bytes)
        .orElseThrow(
            () -> new IllegalArgumentException(variavel + " não é JPEG nem PNG de até 5 MB"));
  }

  private static String variavel(String nome) {
    return Objects.requireNonNull(System.getenv(nome), nome);
  }
}
