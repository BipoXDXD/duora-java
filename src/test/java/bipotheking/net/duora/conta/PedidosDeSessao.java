package bipotheking.net.duora.conta;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Corpos do {@code POST /api/conta/verificacao/sessao} para os testes, com a versão atual do
 * consentimento biométrico lida de {@code legal/biometria.md}: mudar a versão do texto não quebra
 * os testes que só abrem sessão.
 */
public final class PedidosDeSessao {

  private static final Pattern VERSAO = Pattern.compile("^versao:\\s*(\\S+)$", Pattern.MULTILINE);

  /** A versão do front matter de {@code legal/biometria.md}. */
  public static final String VERSAO_DO_CONSENTIMENTO = versaoDoDocumento();

  /** Sessão {@code SELFIE} com consentimento e a versão atual. */
  public static final String SELFIE = corpo("SELFIE");

  /** Sessão {@code DOCUMENTO} com consentimento e a versão atual. */
  public static final String DOCUMENTO = corpo("DOCUMENTO");

  private PedidosDeSessao() {}

  private static String corpo(String finalidade) {
    return "{\"consentimentoBiometrico\":true,\"versaoConsentimento\":\""
        + VERSAO_DO_CONSENTIMENTO
        + "\",\"finalidade\":\""
        + finalidade
        + "\"}";
  }

  private static String versaoDoDocumento() {
    try (InputStream entrada = PedidosDeSessao.class.getResourceAsStream("/legal/biometria.md")) {
      String documento =
          new String(Objects.requireNonNull(entrada).readAllBytes(), StandardCharsets.UTF_8);
      Matcher versao = VERSAO.matcher(documento);
      if (!versao.find()) {
        throw new IllegalStateException("legal/biometria.md sem versao no front matter");
      }
      return versao.group(1);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
