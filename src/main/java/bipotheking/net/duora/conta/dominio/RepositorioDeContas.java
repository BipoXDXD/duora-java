package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.time.LocalDate;
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

  /**
   * Grava a data de nascimento e leva a conta a {@code AGUARDANDO_VERIFICACAO} num UPDATE
   * condicional: só sai de {@code PENDENTE}, e só um entre concorrentes vence.
   *
   * @return se a conta estava {@code PENDENTE} e mudou
   */
  boolean marcarAguardandoVerificacao(ContaId id, LocalDate nascimento);

  /** Leva a conta a {@code EXCLUIDA}, apagando o e-mail e a data de nascimento. */
  void excluir(ContaId id, Instant agora);
}
