package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.EventoDeAcesso;
import bipotheking.net.duora.nucleo.Origem;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Porta dos registros de acesso no banco. Só grava: a aplicação não lê esses registros. */
public interface RepositorioDeAcessos {

  /** Grava o acesso na transação do chamador. */
  void gravar(
      UUID id, Optional<ContaId> conta, Origem origem, Instant instante, EventoDeAcesso evento);
}
