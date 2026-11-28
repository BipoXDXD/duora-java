package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/** Porta das contas no banco. */
public interface RepositorioDeContas {

  /**
   * Cria a conta {@code PENDENTE} com o e-mail. Vazio quando o e-mail já tem conta, inclusive a
   * criada por uma transação concorrente: o insert espera o commit dela e não grava nada.
   */
  Optional<ContaId> criarPendente(Email email);

  Optional<Conta> porEmail(Email email);

  Optional<Conta> porId(ContaId id);

  /**
   * Grava a data de nascimento e leva a conta a {@code AGUARDANDO_VERIFICACAO} num UPDATE
   * condicional: só sai de {@code PENDENTE}, e só um entre concorrentes vence.
   *
   * @return se a conta estava {@code PENDENTE} e mudou
   */
  boolean marcarAguardandoVerificacao(ContaId id, LocalDate nascimento);

  /**
   * Leva a conta a {@code EXCLUIDA}, apagando o e-mail e a data de nascimento, num UPDATE
   * condicional: só sai dos estados disponíveis ({@code PENDENTE}, {@code AGUARDANDO_VERIFICACAO},
   * {@code ATIVA}). Excluir a suspensa ou a banida liberaria o e-mail para um cadastro novo;
   * repetir a exclusão mudaria o instante de onde conta o prazo de retenção. Só mexe na linha da
   * conta: aceites, sessões, links e outbox ficam a cargo do chamador.
   *
   * @return se a conta estava num estado disponível e foi excluída
   */
  boolean excluir(ContaId id, Instant agora);

  /**
   * Leva a conta a {@code ATIVA}, com {@code ativadaEm} = {@code agora}, num UPDATE condicional: só
   * sai de {@code AGUARDANDO_VERIFICACAO}, e só um entre concorrentes vence.
   *
   * @return se a conta estava {@code AGUARDANDO_VERIFICACAO} e mudou
   */
  boolean ativar(ContaId id, Instant agora);
}
