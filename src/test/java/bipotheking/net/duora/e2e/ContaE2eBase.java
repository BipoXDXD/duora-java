package bipotheking.net.duora.e2e;

import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.notificacao.infra.DespachanteDaOutbox;
import bipotheking.net.duora.notificacao.infra.EmailNoLog;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base das jornadas de conta: servidor de verdade numa porta aleatória e o link mágico lido do
 * {@link EmailNoLog}, que guarda os e-mails em vez de entregá-los.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(NavegadorExtension.class)
abstract class ContaE2eBase {

  private static final Duration ESPERA_DO_EMAIL = Duration.ofSeconds(5);
  private static final Duration INTERVALO_DA_CONSULTA = Duration.ofMillis(50);

  /** O token é base64url; o endereço do site no e-mail é o público, não a porta do teste. */
  private static final Pattern LINK_DE_ENTRADA =
      Pattern.compile("/entrar/confirmar#t=([A-Za-z0-9_-]+)");

  @Value("${local.server.port}")
  private int porta;

  @Autowired private DespachanteDaOutbox despachante;
  @Autowired private EmailNoLog emails;

  /** Último token devolvido por e-mail; o JUnit cria uma instância por teste. */
  private final Map<String, String> tokensDevolvidos = new HashMap<>();

  /**
   * O WebKit não guarda cookie {@code Secure} recebido por http, nem em 127.0.0.1; os outros
   * motores tratam o endereço local como seguro e guardam o cookie de produção.
   */
  @DynamicPropertySource
  static void cookieDaSessao(DynamicPropertyRegistry registro) {
    registro.add("duora.sessao.cookie-seguro", () -> !"webkit".equals(Navegador.NOME));
  }

  /** Endereço fictício e único: cada teste parte de um e-mail sem conta nem limite gasto. */
  protected static String emailNovo() {
    return "conta-" + UUID.randomUUID() + "@exemplo.com";
  }

  protected String raiz() {
    return Cenarios.raiz(porta);
  }

  /**
   * O link de entrada mais novo para o endereço que este teste ainda não recebeu, apontado para o
   * servidor do teste. Despacha a outbox na hora em vez de esperar o agendador (a reserva da
   * mensagem impede que os dois a enviem) e espera até 5 s por um link diferente do último
   * devolvido: um segundo pedido para o mesmo e-mail traz o link do segundo pedido.
   */
  protected String linkRecebido(String email) {
    despachante.despacharAgora();
    String chave = email.toLowerCase(Locale.ROOT);
    String anterior = tokensDevolvidos.get(chave);
    Instant limite = Instant.now().plus(ESPERA_DO_EMAIL);
    Optional<String> token = tokenNovo(email, anterior);
    while (token.isEmpty() && Instant.now().isBefore(limite)) {
      pausar();
      token = tokenNovo(email, anterior);
    }
    String novo =
        token.orElseThrow(
            () -> new AssertionError("nenhum link novo para " + email + " em " + ESPERA_DO_EMAIL));
    tokensDevolvidos.put(chave, novo);
    return raiz() + "entrar/confirmar#t=" + novo;
  }

  private Optional<String> tokenNovo(String email, @Nullable String anterior) {
    return emails
        .ultimaPara(email)
        .map(mensagem -> tokenDo(mensagem, email))
        .filter(token -> !token.equals(anterior));
  }

  private static String tokenDo(MensagemDeEmail mensagem, String email) {
    Matcher link = LINK_DE_ENTRADA.matcher(mensagem.texto());
    if (!link.find()) {
      throw new AssertionError("o e-mail para " + email + " não traz o link de entrada");
    }
    return link.group(1);
  }

  private static void pausar() {
    try {
      Thread.sleep(INTERVALO_DA_CONSULTA);
    } catch (InterruptedException interrupcao) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("espera do e-mail interrompida", interrupcao);
    }
  }
}
