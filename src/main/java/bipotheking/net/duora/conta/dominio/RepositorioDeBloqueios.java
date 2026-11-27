package bipotheking.net.duora.conta.dominio;

import java.time.Instant;

/** E-mails bloqueados, guardados só pelo resumo HMAC da chave de bloqueio. */
public interface RepositorioDeBloqueios {

  boolean bloqueado(byte[] resumo);

  /** Bloqueia o resumo; se ele já estava bloqueado, não faz nada. */
  void bloquear(byte[] resumo, String motivo, Instant agora);
}
