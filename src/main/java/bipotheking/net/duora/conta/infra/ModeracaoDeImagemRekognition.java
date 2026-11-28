package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.web.ModeracaoDeImagem;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.DetectModerationLabelsResponse;
import software.amazon.awssdk.services.rekognition.model.ModerationLabel;

/**
 * Moderação automática das fotos pelo {@code DetectModerationLabels} do Amazon Rekognition, com o
 * cliente, a credencial, a região ({@code us-east-1}) e os timeouts da verificação de idade ({@link
 * ClientesAws}): mora em {@code conta/infra} só para reusá-los. A imagem vai em bytes, na chamada;
 * a AWS não a guarda.
 *
 * <p>Libera só quando a AWS respondeu e nenhum rótulo bloqueado chegou ao {@link #LIMIAR}. Qualquer
 * falha (acesso negado, limitação, prazo, rede, imagem recusada) vira {@link
 * ModeracaoDeImagem.Veredito#INDISPONIVEL}: a foto espera a revisão humana, nunca é aprovada. O log
 * diz só a classe e o código do erro; a mensagem da AWS traz ARN e conta e não é copiada.
 *
 * <p>As categorias seguem a "Política de imagens" (sem nudez, sexo, violência explícita ou conteúdo
 * chocante), na taxonomia de três níveis do Rekognition: um rótulo é bloqueado se ele ou o pai dele
 * estiver em {@link #BLOQUEADAS}. Ficam de fora, de propósito, roupa de banho e íntima, costas e
 * peito masculino à mostra, beijo, arma sem violência, bebida, cigarro, gesto e jogo: a política
 * não os proíbe, e cada sinal falso deixa uma foto parada na fila humana.
 */
final class ModeracaoDeImagemRekognition implements ModeracaoDeImagem, AutoCloseable {

  /**
   * Confiança mínima, em %, para um rótulo bloquear (é também o {@code MinConfidence} do pedido). É
   * o padrão da API: um sinal falso só atrasa a foto (ela espera a revisão humana), enquanto um
   * rótulo perdido publica nudez sem ninguém ver; por isso o limiar fica baixo.
   */
  static final float LIMIAR = 50f;

  /** Rótulos de nível 1, 2 ou 3 que mandam a foto para a revisão humana (por nome ou pelo pai). */
  static final Set<String> BLOQUEADAS =
      Set.of(
          // Nudez explícita e sexo: o nível 1 inteiro.
          "Explicit",
          "Explicit Nudity",
          "Explicit Sexual Activity",
          "Sex Toys",
          // Nudez não explícita, sem o peito masculino nem as costas.
          "Obstructed Intimate Parts",
          "Partially Exposed Buttocks",
          "Partially Exposed Female Breast",
          "Implied Nudity",
          // Violência explícita (não basta aparecer uma arma).
          "Graphic Violence",
          // Conteúdo chocante: o nível 1 inteiro, e os símbolos de ódio.
          "Visually Disturbing",
          "Death and Emaciation",
          "Crashes",
          "Hate Symbols");

  private static final Logger LOG = LoggerFactory.getLogger(ModeracaoDeImagemRekognition.class);

  private final RekognitionClient rekognition;

  ModeracaoDeImagemRekognition(RekognitionClient rekognition) {
    this.rekognition = rekognition;
  }

  @Override
  public Veredito avaliar(Path jpeg) {
    try {
      SdkBytes bytes = SdkBytes.fromByteArray(Files.readAllBytes(jpeg));
      DetectModerationLabelsResponse resposta =
          rekognition.detectModerationLabels(
              r -> r.image(i -> i.bytes(bytes)).minConfidence(LIMIAR));
      return resposta.moderationLabels().stream().anyMatch(ModeracaoDeImagemRekognition::bloqueia)
          ? Veredito.SINALIZADA
          : Veredito.LIBERADA;
    } catch (AwsServiceException e) {
      LOG.warn(
          "AWS recusou a moderação de uma foto ({}, código {}): fica para a revisão humana",
          e.getClass().getSimpleName(),
          codigo(e.awsErrorDetails()));
      return Veredito.INDISPONIVEL;
    } catch (IOException | RuntimeException e) {
      LOG.warn(
          "Moderação de uma foto indisponível ({}): fica para a revisão humana",
          e.getClass().getSimpleName());
      return Veredito.INDISPONIVEL;
    }
  }

  @Override
  public void close() {
    rekognition.close();
  }

  private static boolean bloqueia(ModerationLabel rotulo) {
    boolean categoria = bloqueada(rotulo.name()) || bloqueada(rotulo.parentName());
    Float confianca = rotulo.confidence();
    return categoria && confianca != null && confianca >= LIMIAR;
  }

  /** {@code Set.of} recusa {@code contains(null)}; o nível 1 vem sem pai. */
  private static boolean bloqueada(@Nullable String nome) {
    return nome != null && BLOQUEADAS.contains(nome);
  }

  private static String codigo(@Nullable AwsErrorDetails detalhes) {
    if (detalhes == null || detalhes.errorCode() == null) {
      return "desconhecido";
    }
    return detalhes.errorCode();
  }
}
