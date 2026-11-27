package bipotheking.net.duora.conta;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Encarregado pelo tratamento de dados pessoais (LGPD art. 41), publicado nos documentos legais.
 *
 * <p>O {@code @ConfigurationProperties} deixa um marcador não resolvido ({@code
 * ${DUORA_EMAIL_ENCARREGADO}}) passar como texto; por isso o construtor recusa e-mail ausente, em
 * branco ou com cara de marcador, e o boot de produção falha sem a variável.
 */
@ConfigurationProperties("duora.encarregado")
@Validated
public record EncarregadoDeDados(@NotBlank String nome, @Email String email) {

  public EncarregadoDeDados {
    if (email == null || email.isBlank() || email.startsWith("${")) {
      throw new IllegalArgumentException(
          "duora.encarregado.email é obrigatório (em produção, DUORA_EMAIL_ENCARREGADO)");
    }
  }
}
