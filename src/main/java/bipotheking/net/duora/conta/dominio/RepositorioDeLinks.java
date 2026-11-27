package bipotheking.net.duora.conta.dominio;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Links mágicos de entrada, achados pelo resumo do token; o token em si nunca é guardado. */
public interface RepositorioDeLinks {

  /**
   * Trava, até o fim da transação corrente, a gravação de links do e-mail: dois pedidos simultâneos
   * do mesmo e-mail passam um de cada vez, e o segundo invalida o link do primeiro.
   *
   * @param resumoDoEmail o resumo do e-mail, nunca o e-mail: o SQL pode ir para o log
   */
  void travarEmail(String resumoDoEmail);

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
