package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.CodigoCadastro;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Conta como o banco a guarda, com as mesmas invariantes do esquema: só a excluída fica sem e-mail;
 * a data de nascimento existe de {@code AGUARDANDO_VERIFICACAO} a {@code BANIDA}; a ativa tem
 * {@code ativadaEm}. O {@code toString} traz só o id e o estado: o e-mail e a data de nascimento
 * ficam fora dos logs.
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

  /** Data mais antiga aceita no cadastro. */
  private static final LocalDate NASCIMENTO_MINIMO = LocalDate.of(1900, 1, 1);

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

  /**
   * Decide o cadastro com a data de nascimento declarada, sem gravar nada.
   *
   * @param nascimento a data declarada; ausente, recusada depois de conferir o estado
   * @param hoje a data corrente no fuso {@code America/Sao_Paulo}
   * @throws ErroDeNegocio {@code CADASTRO_JA_CONCLUIDO} fora de {@code PENDENTE}, antes de olhar a
   *     data; {@code DATA_INVALIDA} sem data, com data futura ou anterior a 1900-01-01
   */
  public ResultadoDoCadastro concluirCadastro(@Nullable LocalDate nascimento, LocalDate hoje) {
    if (estado != EstadoConta.PENDENTE) {
      throw CodigoCadastro.CADASTRO_JA_CONCLUIDO.erro();
    }
    if (nascimento == null || nascimento.isAfter(hoje) || nascimento.isBefore(NASCIMENTO_MINIMO)) {
      throw CodigoCadastro.DATA_INVALIDA.erro();
    }
    return Idade.maiorDeIdade(nascimento, hoje)
        ? new ResultadoDoCadastro.Concluido()
        : new ResultadoDoCadastro.RecusadoPorIdade();
  }

  /** O que outros módulos podem ver. Ainda não há selo de verificação: sai sempre falso. */
  public ContaResumo resumo() {
    return new ContaResumo(id, estado, dataDeNascimento, false, ativadaEm);
  }

  @Override
  public String toString() {
    return "Conta[id=" + id + ", estado=" + estado + "]";
  }
}
