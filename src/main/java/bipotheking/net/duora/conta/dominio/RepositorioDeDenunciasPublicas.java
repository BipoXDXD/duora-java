package bipotheking.net.duora.conta.dominio;

import java.time.Instant;
import java.util.UUID;

/** Porta das denúncias públicas no banco. A leitura e a resolução ficam com a moderação. */
public interface RepositorioDeDenunciasPublicas {

  /** Grava a denúncia como pendente, na transação do chamador. */
  void gravar(UUID id, DenunciaPublica denuncia, Instant criadaEm);
}
