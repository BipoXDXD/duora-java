package bipotheking.net.duora.push.infra;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.push.CodigoPush;
import bipotheking.net.duora.push.InscricaoDePush;
import bipotheking.net.duora.push.ResultadoDoEnvio;
import bipotheking.net.duora.push.TipoDePush;
import bipotheking.net.duora.web.ServidorHttpLento;
import com.interaso.webpush.VapidKeys;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * O adapter fala com um servidor HTTP local. A allowlist de produção recusa {@code http} e host
 * literal; o teste entra pelo construtor de pacote com uma que só aceita {@code http://127.0.0.1}.
 */
class WebPushInterasoAdapterTest {

  private static final String HOST = "127.0.0.1";

  private final AtomicInteger status = new AtomicInteger(201);
  private final AtomicInteger chegadasNoAlvo = new AtomicInteger();
  private final AtomicReference<Headers> cabecalhos = new AtomicReference<>();
  private final AtomicReference<byte[]> corpo = new AtomicReference<>();

  private ExecutorService threads;
  private HttpServer servidor;
  private KeyPair chavesDoNavegador;
  private byte[] segredoAuth;
  private WebPushInterasoAdapter adapter;

  @BeforeEach
  void subir() throws IOException, GeneralSecurityException {
    threads = Executors.newCachedThreadPool();
    servidor = HttpServer.create(new InetSocketAddress(HOST, 0), 0);
    servidor.setExecutor(threads);
    servidor.createContext("/push", this::receber);
    servidor.createContext("/redireciona", this::redirecionar);
    servidor.createContext("/alvo", this::alvo);
    servidor.start();

    KeyPairGenerator gerador = KeyPairGenerator.getInstance("EC");
    gerador.initialize(new ECGenParameterSpec("secp256r1"));
    chavesDoNavegador = gerador.generateKeyPair();
    segredoAuth = new byte[16];
    new SecureRandom().nextBytes(segredoAuth);

    adapter =
        new WebPushInterasoAdapter(configuracao(), WebPushInterasoAdapterTest::soLoopbackHttp);
  }

  @AfterEach
  void derrubar() {
    servidor.stop(0);
    threads.shutdownNow();
    adapter.close();
  }

  @Test
  void respostaCriadaEntrega() {
    status.set(201);

    assertThat(enviarPara("/push")).isEqualTo(ResultadoDoEnvio.ENTREGUE);
  }

  @Test
  void goneExpira() {
    status.set(410);

    assertThat(enviarPara("/push")).isEqualTo(ResultadoDoEnvio.EXPIRADA);
  }

  @Test
  void naoEncontradoExpira() {
    status.set(404);

    assertThat(enviarPara("/push")).isEqualTo(ResultadoDoEnvio.EXPIRADA);
  }

  @Test
  void erroDoServidorFalha() {
    status.set(500);

    assertThat(enviarPara("/push")).isEqualTo(ResultadoDoEnvio.FALHOU);
  }

  @Test
  void respostaLentaFalhaPeloTimeoutDaRequisicao() {
    try (ServidorHttpLento lento = ServidorHttpLento.iniciar(Duration.ofSeconds(15))) {
      long inicio = System.nanoTime();

      ResultadoDoEnvio resultado = adapter.enviar(inscricao(lento.uri()), TipoDePush.COMPATIVEL);

      assertThat(resultado).isEqualTo(ResultadoDoEnvio.FALHOU);
      assertThat(Duration.ofNanos(System.nanoTime() - inicio)).isLessThan(Duration.ofSeconds(11));
    }
  }

  @Test
  void redirecionamentoNaoESeguido() {
    assertThat(enviarPara("/redireciona")).isEqualTo(ResultadoDoEnvio.FALHOU);
    assertThat(chegadasNoAlvo).hasValue(0);
  }

  @Test
  void cabecalhosTrazemTtlETopic() {
    enviarPara("/push");

    assertThat(cabecalhos.get().getFirst("TTL")).isEqualTo("3600");
    assertThat(cabecalhos.get().getFirst("Topic")).isEqualTo("duora-compativel");
  }

  @Test
  void payloadDecifradoSoTemOTipo() throws GeneralSecurityException {
    enviarPara("/push");

    String claro = new String(decifrar(corpo.get()), StandardCharsets.UTF_8);

    assertThat(claro).isEqualTo("{\"tipo\":\"compativel\"}");
  }

  @Test
  void endpointRecusadoNaRevalidacaoNaoEnvia() {
    URI foraDaAllowlist = URI.create("http://localhost:" + porta() + "/push");

    ResultadoDoEnvio resultado = adapter.enviar(inscricao(foraDaAllowlist), TipoDePush.COMPATIVEL);

    assertThat(resultado).isEqualTo(ResultadoDoEnvio.FALHOU);
    assertThat(corpo.get()).isNull();
  }

  private ResultadoDoEnvio enviarPara(String caminho) {
    URI endpoint = URI.create("http://" + HOST + ":" + porta() + caminho);
    return adapter.enviar(inscricao(endpoint), TipoDePush.COMPATIVEL);
  }

  private int porta() {
    return servidor.getAddress().getPort();
  }

  private InscricaoDePush inscricao(URI endpoint) {
    return new InscricaoDePush(
        UUID.randomUUID(),
        new ContaId(UUID.randomUUID()),
        endpoint,
        base64(semCompressao((ECPublicKey) chavesDoNavegador.getPublic())),
        base64(segredoAuth));
  }

  /** Aceita só {@code http://127.0.0.1}; o resto recusa como a allowlist de produção. */
  private static URI soLoopbackHttp(String bruto) {
    URI uri = URI.create(bruto);
    if (!"http".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) {
      throw CodigoPush.INSCRICAO_INVALIDA.erro();
    }
    return uri;
  }

  private void receber(HttpExchange troca) throws IOException {
    try (troca) {
      cabecalhos.set(troca.getRequestHeaders());
      corpo.set(troca.getRequestBody().readAllBytes());
      troca.sendResponseHeaders(status.get(), -1);
    }
  }

  private void redirecionar(HttpExchange troca) throws IOException {
    try (troca) {
      troca.getResponseHeaders().add("Location", "http://" + HOST + ":" + porta() + "/alvo");
      troca.sendResponseHeaders(302, -1);
    }
  }

  private void alvo(HttpExchange troca) throws IOException {
    try (troca) {
      chegadasNoAlvo.incrementAndGet();
      troca.sendResponseHeaders(201, -1);
    }
  }

  private static ConfiguracaoPush configuracao() {
    VapidKeys vapid = VapidKeys.generate();
    return new ConfiguracaoPush(
        true,
        base64(vapid.getApplicationServerKey()),
        base64(escalar(vapid.getPrivateKey())),
        "mailto:teste@teste.duora.invalid",
        Set.of(HOST));
  }

  /** Decifra o {@code aes128gcm} (RFC 8188/8291) como o navegador faria. */
  private byte[] decifrar(byte[] mensagem) throws GeneralSecurityException {
    ByteBuffer leitura = ByteBuffer.wrap(mensagem);
    byte[] sal = new byte[16];
    leitura.get(sal);
    leitura.getInt(); // tamanho do registro
    byte[] chaveDoServidor = new byte[leitura.get() & 0xff];
    leitura.get(chaveDoServidor);
    byte[] cifrado = new byte[leitura.remaining()];
    leitura.get(cifrado);

    byte[] publicaDoNavegador = semCompressao((ECPublicKey) chavesDoNavegador.getPublic());
    KeyAgreement acordo = KeyAgreement.getInstance("ECDH");
    acordo.init(chavesDoNavegador.getPrivate());
    acordo.doPhase(chavePublica(chaveDoServidor), true);
    byte[] segredoEcdh = acordo.generateSecret();

    byte[] prkChave = hmac(segredoAuth, segredoEcdh);
    byte[] infoChave =
        juntar(
            "WebPush: info\0".getBytes(StandardCharsets.US_ASCII),
            publicaDoNavegador,
            chaveDoServidor,
            new byte[] {1});
    byte[] ikm = hmac(prkChave, infoChave);
    byte[] prk = hmac(sal, ikm);
    byte[] cek =
        Arrays.copyOf(
            hmac(
                prk,
                juntar(
                    "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {1})),
            16);
    byte[] nonce =
        Arrays.copyOf(
            hmac(
                prk,
                juntar(
                    "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII),
                    new byte[] {1})),
            12);

    Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
    aes.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
    byte[] registro = aes.doFinal(cifrado);
    int fim = registro.length - 1;
    while (registro[fim] == 0) {
      fim--;
    }
    assertThat(registro[fim]).as("delimitador do último registro").isEqualTo((byte) 2);
    return Arrays.copyOf(registro, fim);
  }

  private static ECPublicKey chavePublica(byte[] semCompressao) throws GeneralSecurityException {
    ECPublicKey referencia = VapidKeys.generate().getPublicKey();
    ECPoint ponto =
        new ECPoint(
            new BigInteger(1, Arrays.copyOfRange(semCompressao, 1, 33)),
            new BigInteger(1, Arrays.copyOfRange(semCompressao, 33, 65)));
    return (ECPublicKey)
        KeyFactory.getInstance("EC")
            .generatePublic(new ECPublicKeySpec(ponto, referencia.getParams()));
  }

  private static byte[] hmac(byte[] chave, byte[] dados) throws GeneralSecurityException {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(chave, "HmacSHA256"));
    return mac.doFinal(dados);
  }

  private static byte[] juntar(byte[]... partes) {
    ByteBuffer saida = ByteBuffer.allocate(Arrays.stream(partes).mapToInt(p -> p.length).sum());
    Arrays.stream(partes).forEach(saida::put);
    return saida.array();
  }

  static byte[] semCompressao(ECPublicKey chave) {
    return juntar(new byte[] {4}, de32(chave.getW().getAffineX()), de32(chave.getW().getAffineY()));
  }

  static byte[] escalar(ECPrivateKey chave) {
    return de32(chave.getS());
  }

  private static byte[] de32(BigInteger valor) {
    byte[] bruto = valor.toByteArray();
    byte[] saida = new byte[32];
    int copiar = Math.min(bruto.length, 32);
    System.arraycopy(bruto, bruto.length - copiar, saida, 32 - copiar, copiar);
    return saida;
  }

  static String base64(byte[] bytes) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }
}
