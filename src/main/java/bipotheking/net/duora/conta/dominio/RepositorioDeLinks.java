package bipotheking.net.duora.conta.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Links mágicos de entrada, achados pelo resumo do token; o token em si nunca é guardado. */
public interface RepositorioDeLinks {

  /** Marca como usados os links ainda não usados do e-mail. */
  void invalidarAbertos(Email email, Instant agora);

  void gravar(UUID id, Email email, byte[] tokenHash, Instant criadoEm, Instant expiraEm);

  /**
   * Consome o link num UPDATE condicional: só um consumidor concorrente vence.
   *
   * @return o e-mail do link, se ele existia, não estava usado e não tinha vencido
   */
  Optional<Email> consumir(byte[] tokenHash, Instant agora);

  /**
   * Apaga todos os links do e-mail.
   *
   * @return quantos apagou
   */
  int apagarDoEmail(Email email);
}
