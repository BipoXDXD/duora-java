package bipotheking.net.duora.conta.infra;

import bipotheking.net.duora.conta.dominio.ConferenciaDeDocumento;
import bipotheking.net.duora.conta.dominio.FornecedorIndisponivel;
import bipotheking.net.duora.conta.dominio.Imagem;
import bipotheking.net.duora.conta.dominio.ResultadoDoDocumento;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;
import org.springframework.web.client.RestClientException;

/**
 * Conferência do documento pela Didit (API v3). O passo 1, ID Verification, lê frente e verso com a
 * prova de vida do documento ligada e {@code save_api_request=false}, sem {@code vendor_data} nem
 * {@code metadata}: a Didit não guarda nada e as imagens só passam pela memória. Os nomes de
 * arquivo das partes são fixos ({@code frente}, {@code verso}).
 *
 * <p>Sem repetição automática: 5xx, timeout, falha de rede, 401, 403, 429 e qualquer 4xx que não
 * seja o documento irreconhecível viram {@link FornecedorIndisponivel}, sem causa e com motivo
 * fixo. O 400 {@code COULD_NOT_RECOGNIZE_DOCUMENT} é foto ruim, não falha: o documento fica não
 * aceito. Os logs dizem só o passo e o status; nunca o corpo da resposta (dados do documento), a
 * chave ou as imagens.
 */
final class ConferenciaDeDocumentoDidit implements ConferenciaDeDocumento {

  private static final Logger LOG = LoggerFactory.getLogger(ConferenciaDeDocumentoDidit.class);

  private static final String FORNECEDOR = "didit";
  private static final String DOCUMENTO = "documento";
  private static final String CAMINHO_DO_DOCUMENTO = "/v3/id-verification/";
  private static final String CABECALHO_DA_CHAVE = "x-api-key";
  private static final String NAO_RECONHECIDO = "COULD_NOT_RECOGNIZE_DOCUMENT";
  private static final String APROVADO = "Approved";
  private static final Set<String> STATUS_CONHECIDOS = Set.of(APROVADO, "Declined", "In Review");

  private final RestClient documento;
  private final MedicaoDeFornecedor medicao;

  private ConferenciaDeDocumentoDidit(RestClient documento, MedicaoDeFornecedor medicao) {
    this.documento = documento;
    this.medicao = Objects.requireNonNull(medicao, "medicao");
  }

  /**
   * A instância de produção: cliente HTTP do JDK com a conexão da configuração e a leitura própria
   * de cada passo.
   */
  static ConferenciaDeDocumentoDidit comTimeouts(
      RestClient.Builder builder, ConfiguracaoDidit config, MedicaoDeFornecedor medicao) {
    return new ConferenciaDeDocumentoDidit(
        cliente(builder, config, config.leituraDoDocumento()), medicao);
  }

  private static RestClient cliente(
      RestClient.Builder builder, ConfiguracaoDidit config, Duration leitura) {
    String chave = Objects.requireNonNull(config.apiKey(), "duora.verificacao.didit.api-key");
    HttpClientSettings timeouts =
        HttpClientSettings.defaults().withConnectTimeout(config.conexao()).withReadTimeout(leitura);
    return builder
        .clone()
        .baseUrl(config.url())
        .defaultHeader(CABECALHO_DA_CHAVE, chave)
        .requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(timeouts))
        .build();
  }

  @Override
  public ResultadoDoDocumento conferir(Imagem frente, Imagem verso, Imagem selfie) {
    LeituraDoDocumento leitura =
        medicao.medir(FORNECEDOR, DOCUMENTO, () -> lerDocumento(frente, verso));
    return new ResultadoDoDocumento(leitura.aceito(), leitura.dataDeNascimento(), false);
  }

  private LeituraDoDocumento lerDocumento(Imagem frente, Imagem verso) {
    MultipartBodyBuilder partes = new MultipartBodyBuilder();
    partes.part("front_image", frente.bytes(), tipo(frente)).filename("frente");
    partes.part("back_image", verso.bytes(), tipo(verso)).filename("verso");
    partes.part("perform_document_liveness", "true");
    partes.part("save_api_request", "false");
    try {
      return documento
          .post()
          .uri(CAMINHO_DO_DOCUMENTO)
          .contentType(MediaType.MULTIPART_FORM_DATA)
          .body(partes.build())
          .exchangeForRequiredValue((pedido, resposta) -> interpretar(resposta));
    } catch (RestClientException e) {
      LOG.warn(
          "Didit fora de alcance ou com resposta ilegível em {} ({})",
          DOCUMENTO,
          e.getMostSpecificCause().getClass().getSimpleName());
      throw indisponivel(DOCUMENTO);
    }
  }

  private static LeituraDoDocumento interpretar(ConvertibleClientHttpResponse resposta)
      throws IOException {
    int status = resposta.getStatusCode().value();
    if (resposta.getStatusCode().is2xxSuccessful()) {
      RespostaDoDocumento corpo = resposta.bodyTo(RespostaDoDocumento.class);
      return ler(corpo == null ? null : corpo.idVerification());
    }
    if (status == 400 && naoReconheceu(resposta)) {
      LOG.info("Didit não reconheceu o documento em {}: não aceito", DOCUMENTO);
      return LeituraDoDocumento.NAO_ACEITO;
    }
    throw recusa(DOCUMENTO, status);
  }

  /** Só o código do erro interessa; corpo ilegível conta como outro 400. */
  private static boolean naoReconheceu(ConvertibleClientHttpResponse resposta) {
    try {
      ErroDaDidit erro = resposta.bodyTo(ErroDaDidit.class);
      return erro != null && NAO_RECONHECIDO.equals(erro.error());
    } catch (RestClientException e) {
      return false;
    }
  }

  private static LeituraDoDocumento ler(@Nullable IdVerification leitura) {
    if (leitura == null) {
      LOG.warn("Didit respondeu sem id_verification em {}: documento não aceito", DOCUMENTO);
      return LeituraDoDocumento.NAO_ACEITO;
    }
    @Nullable String status = leitura.status();
    if (status == null || !STATUS_CONHECIDOS.contains(status)) {
      LOG.warn("Didit devolveu status desconhecido em {}: documento não aceito", DOCUMENTO);
    }
    boolean aceito = APROVADO.equals(status);
    return new LeituraDoDocumento(
        aceito, data(leitura.dataDeNascimento()), retrato(leitura.retrato(), aceito));
  }

  private static Optional<LocalDate> data(@Nullable String texto) {
    if (texto == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(LocalDate.parse(texto));
    } catch (DateTimeParseException e) {
      LOG.warn("Didit devolveu data de nascimento fora do formato ISO em {}: sem data", DOCUMENTO);
      return Optional.empty();
    }
  }

  /**
   * O retrato vem em base64 com {@code save_api_request=false}. Ausente num documento recusado é
   * normal; ausente num aceito, ou presente mas ilegível, merece aviso.
   */
  private static Optional<Imagem> retrato(@Nullable String base64, boolean aceito) {
    if (base64 == null) {
      if (aceito) {
        LOG.warn("Didit aceitou o documento sem retrato em {}", DOCUMENTO);
      }
      return Optional.empty();
    }
    Optional<Imagem> retrato;
    try {
      retrato = Imagem.ler(Base64.getDecoder().decode(base64));
    } catch (IllegalArgumentException e) {
      retrato = Optional.empty();
    }
    if (retrato.isEmpty()) {
      LOG.warn("Didit devolveu retrato que não é JPEG nem PNG de até 5 MB em {}", DOCUMENTO);
    }
    return retrato;
  }

  /** Loga o status (nunca o corpo) e devolve a falha a lançar. */
  private static FornecedorIndisponivel recusa(String passo, int status) {
    if (status == 401 || status == 403) {
      LOG.error(
          "Didit negou {} ({}): confira duora.verificacao.didit.api-key e o saldo da conta",
          passo,
          status);
    } else if (status == 429 || status >= 500) {
      LOG.warn("Didit indisponível em {} ({})", passo, status);
    } else {
      LOG.error("Didit recusou o pedido em {} ({}): pedido malformado", passo, status);
    }
    return indisponivel(passo);
  }

  private static MediaType tipo(Imagem imagem) {
    return switch (imagem.tipo()) {
      case JPEG -> MediaType.IMAGE_JPEG;
      case PNG -> MediaType.IMAGE_PNG;
    };
  }

  private static FornecedorIndisponivel indisponivel(String passo) {
    return new FornecedorIndisponivel("Didit indisponível em " + passo, null);
  }

  /** O que o passo 1 leu; o retrato fica para o Face Match. */
  private record LeituraDoDocumento(
      boolean aceito, Optional<LocalDate> dataDeNascimento, Optional<Imagem> retrato) {

    static final LeituraDoDocumento NAO_ACEITO =
        new LeituraDoDocumento(false, Optional.empty(), Optional.empty());
  }

  /** Só os campos que interessam; a resposta traz nome, número e imagens do documento. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record RespostaDoDocumento(
      @JsonProperty("id_verification") @Nullable IdVerification idVerification) {}

  /** O {@code toString} esconde a data e o retrato. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record IdVerification(
      @Nullable String status,
      @JsonProperty("date_of_birth") @Nullable String dataDeNascimento,
      @JsonProperty("portrait_image") @Nullable String retrato) {

    @Override
    public String toString() {
      return "IdVerification[status=" + status + ", dataDeNascimento=***, retrato=***]";
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ErroDaDidit(@Nullable String error) {}
}
