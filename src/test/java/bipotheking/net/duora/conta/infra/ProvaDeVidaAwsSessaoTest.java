package bipotheking.net.duora.conta.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import bipotheking.net.duora.conta.dominio.CredencialCurta;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.ModoDeCaptura;
import bipotheking.net.duora.conta.dominio.SessaoDeProva;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.rekognition.model.AccessDeniedException;
import software.amazon.awssdk.services.rekognition.model.CreateFaceLivenessSessionRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.StsException;

/** Abertura da sessão de prova de vida e emissão da credencial curta, contra clientes falsos. */
@ExtendWith(OutputCaptureExtension.class)
class ProvaDeVidaAwsSessaoTest {

  private static final UUID TENTATIVA = UUID.fromString("0193f5c2-7a10-7000-8000-000000000001");
  private static final String PAPEL = "arn:aws:iam::000000000000:role/duora-captura";
  private static final String SESSAO = "sessao-falsa";
  private static final Instant EXPIRA_EM = Instant.parse("2026-11-27T12:15:00Z");
  private static final String POLITICA =
      "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\","
          + "\"Action\":\"rekognition:StartFaceLivenessSession\",\"Resource\":\"*\"}]}";

  private final SimpleMeterRegistry metricas = new SimpleMeterRegistry();

  @Test
  void modoEAws() {
    ProvaDeVidaAws adapter = adapter(RekognitionFalso.respondendo(SESSAO), stsQueResponde());

    assertThat(adapter.modo()).isEqualTo(ModoDeCaptura.AWS);
  }

  @Test
  void criarSessaoPedeZeroImagensDeAuditoriaESemS3() {
    RekognitionFalso rekognition = RekognitionFalso.respondendo(SESSAO);

    adapter(rekognition, stsQueResponde()).criarSessao(TENTATIVA);

    CreateFaceLivenessSessionRequest pedido = rekognition.pedido();
    assertThat(pedido.settings().auditImagesLimit()).isZero();
    assertThat(pedido.settings().outputConfig()).isNull();
    assertThat(pedido.kmsKeyId()).isNull();
  }

  @Test
  void tokenDeIdempotenciaEOIdDaTentativa() {
    RekognitionFalso rekognition = RekognitionFalso.respondendo(SESSAO);

    adapter(rekognition, stsQueResponde()).criarSessao(TENTATIVA);

    assertThat(rekognition.pedido().clientRequestToken())
        .isEqualTo("0193f5c2-7a10-7000-8000-000000000001");
  }

  @Test
  void credencialVemDoAssumeRoleCom900s() {
    StsFalso sts = stsQueResponde();

    adapter(RekognitionFalso.respondendo(SESSAO), sts).emitir(TENTATIVA);

    AssumeRoleRequest pedido = sts.pedido();
    assertThat(pedido.roleArn()).isEqualTo(PAPEL);
    assertThat(pedido.roleSessionName()).isEqualTo("duora-0193f5c2-7a10-7000-8000-000000000001");
    assertThat(pedido.durationSeconds()).isEqualTo(900);
    assertThat(pedido.policy()).isEqualTo(POLITICA);
  }

  @Test
  void devolveSessaoECredencial() {
    ProvaDeVidaAws adapter = adapter(RekognitionFalso.respondendo(SESSAO), stsQueResponde());

    SessaoDeProva sessao = adapter.criarSessao(TENTATIVA);
    CredencialCurta credencial = adapter.emitir(TENTATIVA);

    assertThat(sessao.id()).isEqualTo(SESSAO);
    assertThat(sessao.regiao()).isEqualTo("us-east-1");
    assertThat(credencial.accessKeyId()).isEqualTo("ASIAFALSA");
    assertThat(credencial.secretAccessKey()).isEqualTo("segredo-falso");
    assertThat(credencial.sessionToken()).isEqualTo("token-falso");
    assertThat(credencial.expiraEm()).isEqualTo(EXPIRA_EM);
  }

  @Test
  void falhaDoCreateViraIndisponivel() {
    ProvaDeVidaAws adapter =
        adapter(RekognitionFalso.falhando(SdkClientException.create("sem rede")), stsQueResponde());

    Throwable falha = catchThrowable(() -> adapter.criarSessao(TENTATIVA));

    assertThat(falha).isExactlyInstanceOf(FornecedorIndisponivel.class);
  }

  @Test
  void falhaDoAssumeRoleViraIndisponivel() {
    StsFalso sts =
        StsFalso.falhando(
            (RuntimeException)
                StsException.builder().statusCode(500).message("erro interno").build());
    ProvaDeVidaAws adapter = adapter(RekognitionFalso.respondendo(SESSAO), sts);

    Throwable falha = catchThrowable(() -> adapter.emitir(TENTATIVA));

    assertThat(falha).isExactlyInstanceOf(FornecedorIndisponivel.class);
  }

  @Test
  void acessoNegadoLogaErroSemSegredo(CapturedOutput saida) {
    String mensagem =
        "User: arn:aws:iam::123456789012:user/canario is not authorized to perform:"
            + " rekognition:CreateFaceLivenessSession";
    RuntimeException negado =
        (RuntimeException)
            AccessDeniedException.builder()
                .statusCode(403)
                .message(mensagem)
                .awsErrorDetails(
                    AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .errorMessage(mensagem)
                        .build())
                .build();
    ProvaDeVidaAws adapter = adapter(RekognitionFalso.falhando(negado), stsQueResponde());

    Throwable falha = catchThrowable(() -> adapter.criarSessao(TENTATIVA));

    assertThat(falha).isExactlyInstanceOf(FornecedorIndisponivel.class);
    assertThat(saida).contains("ERROR").contains("confira IAM").doesNotContain("123456789012");
    assertThat(stackTrace(falha)).doesNotContain("123456789012");
  }

  @Test
  void metricaContaSucessoDasDuasOperacoes() {
    ProvaDeVidaAws adapter = adapter(RekognitionFalso.respondendo(SESSAO), stsQueResponde());

    adapter.criarSessao(TENTATIVA);
    adapter.emitir(TENTATIVA);

    assertThat(contagem("criar-sessao", "sucesso")).isEqualTo(1);
    assertThat(contagem("credencial", "sucesso")).isEqualTo(1);
  }

  @Test
  void closeFechaOsDoisClientes() {
    RekognitionFalso rekognition = RekognitionFalso.respondendo(SESSAO);
    StsFalso sts = stsQueResponde();

    adapter(rekognition, sts).close();

    assertThat(rekognition.fechado()).isTrue();
    assertThat(sts.fechado()).isTrue();
  }

  private ProvaDeVidaAws adapter(RekognitionFalso rekognition, StsFalso sts) {
    return new ProvaDeVidaAws(rekognition, sts, PAPEL, new MedicaoDeFornecedor(metricas));
  }

  private static StsFalso stsQueResponde() {
    return StsFalso.respondendo(
        Credentials.builder()
            .accessKeyId("ASIAFALSA")
            .secretAccessKey("segredo-falso")
            .sessionToken("token-falso")
            .expiration(EXPIRA_EM)
            .build());
  }

  private long contagem(String operacao, String desfecho) {
    @Nullable Timer timer =
        metricas
            .find("duora.verificacao.fornecedor")
            .tags("fornecedor", "aws", "operacao", operacao, "desfecho", desfecho)
            .timer();
    return timer == null ? 0 : timer.count();
  }

  private static String stackTrace(Throwable falha) {
    StringWriter texto = new StringWriter();
    falha.printStackTrace(new PrintWriter(texto));
    return texto.toString();
  }
}
