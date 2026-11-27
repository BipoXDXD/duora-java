package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Conta como o banco a guarda, com as mesmas invariantes do esquema: só a excluída fica sem e-mail;
 * a data de nascimento existe de {@code AGUARDANDO_VERIFICACAO} a {@code BANIDA}; a ativa tem
 * {@code ativadaEm}. Não logue: o {@code toString} traz o e-mail e a data de nascimento.
 */
public record Conta(
    ContaId id,
    @Nullable Email email,
    EstadoConta estado,
    @Nullable LocalDate dataDeNascimento,
    Instant criadaEm,
    @Nullable Instant ativadaEm) {

  /** Estados em que o cadastro já gravou a data de nascimento. */
  private static final Set<EstadoConta> COM_NASCIMENTO =
      EnumSet.of(
          EstadoConta.AGUARDANDO_VERIFICACAO,
          EstadoConta.ATIVA,
          EstadoConta.SUSPENSA,
          EstadoConta.BANIDA);

  /**
   * @throws IllegalStateException se alguma invariante for violada (a mensagem não traz dados
   *     pessoais)
   */
  public Conta {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(estado, "estado");
    Objects.requireNonNull(criadaEm, "criadaEm");
    if ((estado == EstadoConta.EXCLUIDA) != (email == null)) {
      throw new IllegalStateException("Só a conta excluída fica sem e-mail: " + estado);
    }
    if (COM_NASCIMENTO.contains(estado) && dataDeNascimento == null) {
      throw new IllegalStateException("Conta sem data de nascimento: " + estado);
    }
    if (estado == EstadoConta.ATIVA && ativadaEm == null) {
      throw new IllegalStateException("Conta ativa sem instante de ativação");
    }
  }

  /** O que outros módulos podem ver. Ainda não há selo de verificação: sai sempre falso. */
  public ContaResumo resumo() {
    return new ContaResumo(id, estado, dataDeNascimento, false, ativadaEm);
  }
}
