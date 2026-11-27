package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.conta.ContaResumo;
import bipotheking.net.duora.conta.EstadoConta;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Conta como o banco a guarda. Só a excluída fica sem e-mail; a data de nascimento existe a partir
 * de {@code AGUARDANDO_VERIFICACAO} (exceto na excluída). O esquema garante as duas regras. Não
 * logue: o {@code toString} traz o e-mail e a data de nascimento.
 */
public record Conta(
    ContaId id,
    @Nullable Email email,
    EstadoConta estado,
    @Nullable LocalDate dataDeNascimento,
    Instant criadaEm,
    @Nullable Instant ativadaEm) {

  /** O que outros módulos podem ver. Ainda não há selo de verificação: sai sempre falso. */
  public ContaResumo resumo() {
    return new ContaResumo(id, estado, dataDeNascimento, false, ativadaEm);
  }
}
