package bipotheking.net.duora.push.infra;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Web Push ({@code duora.push.*}, ADR 0009). Desligado, o envio só escreve no log; é o padrão,
 * inclusive em produção, para o boot não depender das chaves VAPID. Ligado sem as duas chaves, sem
 * assunto ou sem hosts permitidos, o boot cai: o Binder deixa passar {@code ${...}} literal, e todo
 * envio falharia só na hora de entregar.
 *
 * @param chavePublica a chave pública VAPID, ponto P-256 sem compressão em base64url
 * @param chavePrivada a chave privada VAPID, escalar de 32 bytes em base64url
 * @param assunto o {@code sub} do JWT VAPID, como {@code mailto:...}
 * @param hostsPermitidos a allowlist de {@link bipotheking.net.duora.push.EnderecoDePush}
 */
@ConfigurationProperties("duora.push")
record ConfiguracaoPush(
    boolean habilitado,
    @Nullable String chavePublica,
    @Nullable String chavePrivada,
    @Nullable String assunto,
    @DefaultValue Set<String> hostsPermitidos) {

  ConfiguracaoPush {
    hostsPermitidos = Set.copyOf(hostsPermitidos);
    if (habilitado) {
      exigir(chavePublica, "duora.push.chave-publica");
      exigir(chavePrivada, "duora.push.chave-privada");
      exigir(assunto, "duora.push.assunto");
      if (hostsPermitidos.isEmpty()) {
        throw new IllegalArgumentException("duora.push.hosts-permitidos vazio");
      }
    }
  }

  /** A mensagem cita só a propriedade, nunca o valor (pode ser a chave). */
  private static void exigir(@Nullable String valor, String propriedade) {
    if (valor == null || valor.isBlank() || valor.startsWith("${")) {
      throw new IllegalArgumentException(propriedade + " ausente ou com placeholder sem resolver");
    }
  }

  /** Não imprime a chave privada. */
  @Override
  public String toString() {
    return "ConfiguracaoPush[habilitado="
        + habilitado
        + ", assunto="
        + assunto
        + ", hostsPermitidos="
        + hostsPermitidos
        + "]";
  }
}
