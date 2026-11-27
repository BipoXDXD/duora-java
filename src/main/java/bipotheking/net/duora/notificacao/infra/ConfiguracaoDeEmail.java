package bipotheking.net.duora.notificacao.infra;

import java.time.Duration;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Envio de e-mail ({@code duora.email.*}). Dev e test escrevem no log; prod usa o Resend, com a
 * chave do ambiente. Provedor desconhecido, remetente ausente ou Resend sem chave derrubam o boot:
 * o Binder deixa passar {@code ${...}} literal, e todo envio falharia só na hora de entregar.
 *
 * @param provedor {@code log} ou {@code resend}
 * @param remetente o {@code from} dos e-mails, como {@code Nome <endereco>}
 */
@ConfigurationProperties("duora.email")
record ConfiguracaoDeEmail(String provedor, String remetente, @DefaultValue Resend resend) {

  static final String LOG = "log";
  static final String RESEND = "resend";

  ConfiguracaoDeEmail {
    // Set.of recusa contains(null): o nulo é testado antes.
    if (provedor == null || !Set.of(LOG, RESEND).contains(provedor)) {
      throw new IllegalArgumentException(
          "duora.email.provedor deve ser " + LOG + " ou " + RESEND + ", veio " + provedor);
    }
    exigir(remetente, "duora.email.remetente");
    // O Binder sempre preenche (@DefaultValue); nulo só vem de construção direta.
    if (resend == null) {
      throw new IllegalArgumentException("duora.email.resend ausente");
    }
    if (RESEND.equals(provedor)) {
      exigir(resend.chave(), "duora.email.resend.chave");
    }
  }

  /** A mensagem cita só a propriedade, nunca o valor (pode ser a chave). */
  private static void exigir(@Nullable String valor, String propriedade) {
    if (valor == null || valor.isBlank() || valor.startsWith("${")) {
      throw new IllegalArgumentException(propriedade + " ausente ou com placeholder sem resolver");
    }
  }

  /**
   * Acesso à API do Resend.
   *
   * @param chave a API key; obrigatória só com {@code provedor=resend}
   */
  record Resend(
      @Nullable String chave,
      @DefaultValue("PT5S") Duration conexao,
      @DefaultValue("PT10S") Duration leitura) {

    /** Não imprime a chave. */
    @Override
    public String toString() {
      return "Resend[conexao=" + conexao + ", leitura=" + leitura + "]";
    }
  }
}
