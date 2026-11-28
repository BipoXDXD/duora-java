package bipotheking.net.duora.conta.web;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContasDeTeste;
import bipotheking.net.duora.conta.ContasDeTeste.ContaDeTeste;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.web.MesmaOrigem;
import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.unit.DataSize;

/**
 * Multipart do envio do documento no Tomcat de verdade: os limites, a leitura só em memória e a
 * resolução preguiçosa dependem do servidor embutido, que o MockMvc não usa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultipartEmMemoriaTest {

  private static final String ROTA = "/api/conta/verificacao/documento";
  private static final String FRONTEIRA = "fronteira-de-teste";
  private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

  private final HttpClient cliente = HttpClient.newHttpClient();

  @Autowired private MultipartProperties multipart;
  @Autowired private ContasDeTeste contas;

  @Value("${local.server.port}")
  private int porta;

  @Test
  void limitesSao6MbPorArquivoE13MbPorRequisicao() {
    assertThat(multipart.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(6));
    assertThat(multipart.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(13));
  }

  /** Com o limiar no tamanho máximo da requisição, nenhuma imagem vai para o disco. */
  @Test
  void arquivosFicamEmMemoria() {
    assertThat(multipart.getFileSizeThreshold().toBytes())
        .isGreaterThanOrEqualTo(multipart.getMaxRequestSize().toBytes());
  }

  @Test
  void multipartSoEhLidoQuandoOHandlerPede() {
    assertThat(multipart.isResolveLazily()).isTrue();
  }

  @Test
  void arquivoAcimaDe6MbRecebe413() throws Exception {
    byte[] grande = new byte[(int) DataSize.ofMegabytes(6).toBytes() + 1];
    System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

    HttpResponse<String> resposta = enviar(sessaoDeConta(), grande, JPEG);

    assertThat(resposta.statusCode()).isEqualTo(413);
    assertThat(JsonPath.<String>read(resposta.body(), "$.codigo"))
        .isEqualTo("IMAGEM_GRANDE_DEMAIS");
  }

  /** Entre 5 MB e 6 MB, o Tomcat aceita e o caso de uso recusa a imagem. */
  @Test
  void imagemEntre5E6MbChegaAoCasoDeUso() throws Exception {
    byte[] grande = new byte[Imagem.TAMANHO_MAXIMO + 1];
    System.arraycopy(JPEG, 0, grande, 0, JPEG.length);

    HttpResponse<String> resposta = enviar(sessaoDeConta(), grande, JPEG);

    assertThat(resposta.statusCode()).isEqualTo(422);
    assertThat(JsonPath.<String>read(resposta.body(), "$.codigo")).isEqualTo("DOCUMENTO_INVALIDO");
  }

  /**
   * Um multipart cortado no meio da frente recebe 401 sem sessão: a sessão é conferida antes de o
   * corpo ser lido. Com a resolução antecipada, a análise do corpo falharia primeiro.
   */
  @Test
  void semSessaoRecusaAntesDeLerOCorpo() throws Exception {
    HttpRequest pedido =
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + ROTA))
            .header(MesmaOrigem.CABECALHO, "1")
            .header("Content-Type", "multipart/form-data; boundary=" + FRONTEIRA)
            .POST(HttpRequest.BodyPublishers.ofByteArray(cortado()))
            .build();

    HttpResponse<String> resposta = cliente.send(pedido, HttpResponse.BodyHandlers.ofString());

    assertThat(resposta.statusCode()).isEqualTo(401);
    assertThat(JsonPath.<String>read(resposta.body(), "$.codigo")).isEqualTo("NAO_AUTENTICADO");
  }

  private String sessaoDeConta() {
    ContaDeTeste conta =
        contas.criar(EstadoConta.AGUARDANDO_VERIFICACAO, LocalDate.of(1990, 5, 10));
    return conta.cookie();
  }

  private HttpResponse<String> enviar(String cookie, byte[] frente, byte[] verso) throws Exception {
    HttpRequest pedido =
        HttpRequest.newBuilder(URI.create("http://localhost:" + porta + ROTA))
            .header(MesmaOrigem.CABECALHO, "1")
            .header("Cookie", cookie)
            .header("Content-Type", "multipart/form-data; boundary=" + FRONTEIRA)
            .POST(HttpRequest.BodyPublishers.ofByteArray(corpo(frente, verso)))
            .build();
    return cliente.send(pedido, HttpResponse.BodyHandlers.ofString());
  }

  private static byte[] corpo(byte[] frente, byte[] verso) {
    ByteArrayOutputStream corpo = new ByteArrayOutputStream();
    escrever(corpo, "--" + FRONTEIRA + "\r\n");
    escrever(corpo, "Content-Disposition: form-data; name=\"sessaoId\"\r\n\r\n");
    escrever(corpo, "sessao-qualquer\r\n");
    arquivo(corpo, "frente", frente);
    arquivo(corpo, "verso", verso);
    escrever(corpo, "--" + FRONTEIRA + "--\r\n");
    return corpo.toByteArray();
  }

  /** Abre a parte da frente e termina sem a fronteira final: a análise do multipart falha. */
  private static byte[] cortado() {
    ByteArrayOutputStream corpo = new ByteArrayOutputStream();
    escrever(corpo, "--" + FRONTEIRA + "\r\n");
    escrever(corpo, "Content-Disposition: form-data; name=\"frente\"; filename=\"frente.jpg\"\r\n");
    escrever(corpo, "Content-Type: image/jpeg\r\n\r\n");
    corpo.writeBytes(JPEG);
    return corpo.toByteArray();
  }

  private static void arquivo(ByteArrayOutputStream corpo, String nome, byte[] bytes) {
    escrever(corpo, "--" + FRONTEIRA + "\r\n");
    escrever(
        corpo,
        "Content-Disposition: form-data; name=\"" + nome + "\"; filename=\"" + nome + ".jpg\"\r\n");
    escrever(corpo, "Content-Type: image/jpeg\r\n\r\n");
    corpo.writeBytes(bytes);
    escrever(corpo, "\r\n");
  }

  private static void escrever(ByteArrayOutputStream corpo, String texto) {
    corpo.writeBytes(texto.getBytes(StandardCharsets.UTF_8));
  }
}
