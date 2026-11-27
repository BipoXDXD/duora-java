package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.util.Optional;

/** Porta das contas no banco. */
public interface RepositorioDeContas {

  /**
   * Cria a conta {@code PENDENTE} com o e-mail. Se o e-mail já tem conta, o banco recusa (no
   * Spring, {@code DuplicateKeyException}).
   */
  ContaId criarPendente(Email email);

  Optional<Conta> porEmail(Email email);

  Optional<Conta> porId(ContaId id);
}
