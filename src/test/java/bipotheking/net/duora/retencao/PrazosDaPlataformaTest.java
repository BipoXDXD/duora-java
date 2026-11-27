package bipotheking.net.duora.retencao;

import static org.assertj.core.api.Assertions.assertThat;

import bipotheking.net.duora.web.BancoDeTeste;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Os prazos das políticas de retenção batem com a tabela "Por quanto tempo guardamos" da política
 * de privacidade, lida do próprio documento publicado.
 */
class PrazosDaPlataformaTest extends BancoDeTeste {

  private static final String POLITICA_DE_PRIVACIDADE = "/legal/privacidade.md";
  private static final String SECAO = "## Por quanto tempo guardamos";

  /**
   * Linha da tabela para as políticas que a cumprem, na ordem dos prazos que a linha cita. As
   * linhas de fora (chat, imagens, verificação de idade) são de etapas que ainda não têm política.
   */
  private static final Map<String, List<String>> POLITICAS_POR_LINHA =
      Map.of(
          "Link de entrada", List.of("links-de-entrada"),
          "Sessão", List.of("sessoes-expiradas"),
          "E-mails na fila de envio", List.of("outbox-enviada", "outbox-falhada"),
          "Chaves de repetição de pedidos", List.of("chaves-de-idempotencia"),
          "Registros de acesso (IP, porta e horário)", List.of("registros-de-acesso"),
          "Denúncias feitas sem conta", List.of("denuncias-publicas"));

  /**
   * Políticas sem linha na tabela: divergência conhecida, à espera da decisão do dono sobre o texto
   * legal. Quando a linha entrar na política de privacidade, ela sai daqui e vai para {@link
   * #POLITICAS_POR_LINHA}.
   */
  private static final Map<String, Duration> SEM_LINHA_NA_POLITICA =
      Map.of("uso-conta-nova", Duration.parse("P8D"));

  private static final Pattern PRAZO = Pattern.compile("(\\d+) (minutos?|horas?|dias?|mês|meses)");

  /** Seis meses são os 183 dias do Marco Civil: um mês conta 30,5 dias (732 horas). */
  private static final Duration UM_MES = Duration.ofHours(732);

  @Autowired private ExpiracaoDeDados expiracao;

  @Test
  void prazosDaPlataformaBatemComAPoliticaDePrivacidade() throws IOException {
    Map<String, String> tabela = tabelaDeRetencao();
    assertThat(tabela).containsKeys(POLITICAS_POR_LINHA.keySet().toArray(String[]::new));

    Map<String, Duration> esperados = new HashMap<>(SEM_LINHA_NA_POLITICA);
    POLITICAS_POR_LINHA.forEach((dado, politicas) -> {
      List<Duration> prazos = prazosDaRetencao(Objects.requireNonNull(tabela.get(dado)));
      assertThat(prazos).as("prazos da linha %s", dado).hasSameSizeAs(politicas);
      for (int i = 0; i < politicas.size(); i++) {
        esperados.put(politicas.get(i), prazos.get(i));
      }
    });

    assertThat(expiracao.prazos()).containsExactlyInAnyOrderEntriesOf(esperados);
  }

  /** As linhas da tabela da seção de retenção: dado para o texto do prazo. */
  private static Map<String, String> tabelaDeRetencao() throws IOException {
    String documento;
    try (InputStream entrada =
        PrazosDaPlataformaTest.class.getResourceAsStream(POLITICA_DE_PRIVACIDADE)) {
      documento =
          new String(Objects.requireNonNull(entrada).readAllBytes(), StandardCharsets.UTF_8);
    }
    String secao = documento.substring(documento.indexOf(SECAO) + SECAO.length());
    Map<String, String> linhas = new LinkedHashMap<>();
    for (String linha : secao.lines().dropWhile(l -> !l.startsWith("|")).toList()) {
      if (!linha.startsWith("|")) {
        break;
      }
      String[] colunas = linha.split("\\|");
      String dado = colunas[1].strip();
      if (!dado.equals("Dado") && !dado.startsWith("---")) {
        linhas.put(dado, colunas[2].strip());
      }
    }
    return linhas;
  }

  /**
   * Os prazos de retenção citados no texto. Antes do ponto e vírgula vem a validade (do link, da
   * sessão), não a retenção; "ao vencer" é prazo zero depois do vencimento.
   */
  private static List<Duration> prazosDaRetencao(String texto) {
    String retencao = texto.substring(texto.lastIndexOf(';') + 1);
    if (retencao.contains("ao vencer")) {
      return List.of(Duration.ZERO);
    }
    List<Duration> prazos = new ArrayList<>();
    Matcher prazo = PRAZO.matcher(retencao);
    while (prazo.find()) {
      long quantidade = Long.parseLong(prazo.group(1));
      String unidade = prazo.group(2);
      prazos.add(
          switch (unidade.charAt(0)) {
            case 'm' ->
                unidade.startsWith("min")
                    ? Duration.ofMinutes(quantidade)
                    : UM_MES.multipliedBy(quantidade);
            case 'h' -> Duration.ofHours(quantidade);
            default -> Duration.ofDays(quantidade);
          });
    }
    return prazos;
  }
}
