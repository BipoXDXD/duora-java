package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * O que uma decisão de idade muda na conta, pela selfie ou pelo documento, na transação do
 * chamador: aprovada ativa a conta e grava a aprovação; recusada exclui a conta e bloqueia o
 * e-mail, como o cadastro de menor. Dúvida e repetição não mudam a conta.
 */
@Component
class EfeitosDaVerificacao {

  private static final String MOTIVO_DO_BLOQUEIO = "MENOR";

  private final RepositorioDeContas contas;
  private final RepositorioDeVerificacoes verificacoes;
  private final LimpezaDeConta limpeza;

  EfeitosDaVerificacao(
      RepositorioDeContas contas, RepositorioDeVerificacoes verificacoes, LimpezaDeConta limpeza) {
    this.contas = contas;
    this.verificacoes = verificacoes;
    this.limpeza = limpeza;
  }

  /**
   * Aplica a decisão. Sem transação aberta, falha antes de gravar: a ativação sem a aprovação (ou a
   * exclusão pela metade) deixaria a conta incoerente.
   *
   * @throws ErroDeNegocio {@code VERIFICACAO_FORA_DE_ORDEM} se a aprovação chega com a conta fora
   *     de {@code AGUARDANDO_VERIFICACAO}; o chamador desfaz a transação
   * @throws IllegalStateException sem transação ativa
   */
  void aplicar(
      Conta conta, ResultadoDaVerificacao resultado, MetodoDeVerificacao metodo, Instant agora) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Os efeitos da verificação exigem a transação do chamador");
    }
    switch (resultado) {
      case APROVADA -> {
        if (!contas.ativar(conta.id(), agora)) {
          throw CodigoVerificacao.VERIFICACAO_FORA_DE_ORDEM.erro();
        }
        verificacoes.gravarAprovacao(conta.id(), metodo, agora);
      }
      case RECUSADA ->
          limpeza.excluirEBloquear(conta.id(), emailDe(conta), MOTIVO_DO_BLOQUEIO, agora);
      case DUVIDA, REPETIR -> {
        // A conta segue aguardando; só a tentativa guarda o resultado.
      }
    }
  }

  /** A conta aguardando verificação sempre tem e-mail: a invariante da {@link Conta} garante. */
  private static Email emailDe(Conta conta) {
    return Objects.requireNonNull(conta.email(), "Conta aguardando verificação sem e-mail");
  }
}
