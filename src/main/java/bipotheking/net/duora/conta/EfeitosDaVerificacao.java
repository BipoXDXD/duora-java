package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.MetodoDeVerificacao;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
import bipotheking.net.duora.conta.dominio.ResultadoDaVerificacao;
import bipotheking.net.duora.conta.dominio.Tentativa;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * A conclusão da tentativa e o que a decisão de idade muda na conta, pela selfie ou pelo documento,
 * na transação do chamador: aprovada ativa a conta e grava a aprovação; recusada exclui a conta e
 * bloqueia o e-mail, como o cadastro de menor. Dúvida e repetição não mudam a conta.
 *
 * <p>As travas seguem a ordem da exclusão da conta ({@link LimpezaDeConta}): o e-mail (só na
 * recusa, que exclui), a conta e, por último, a tentativa. Com uma ordem só, a conclusão e uma
 * exclusão paralela da mesma conta esperam uma pela outra em vez de travarem.
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
   * Conclui a tentativa com o resultado e aplica a decisão. A conta é relida já travada: a
   * aprovação ou a recusa só valem com ela em {@code AGUARDANDO_VERIFICACAO}, porque outra
   * tentativa pode tê-la ativado enquanto esta lia o fornecedor. Sem transação aberta, falha antes
   * de gravar: a ativação sem a aprovação (ou a exclusão pela metade) deixaria a conta incoerente.
   *
   * @param conta a conta lida antes do fornecedor; dela só sai o e-mail que a recusa trava
   * @throws ErroDeNegocio {@code VERIFICACAO_FORA_DE_ORDEM} se a aprovação ou a recusa chega com a
   *     conta fora de {@code AGUARDANDO_VERIFICACAO}; {@code SESSAO_DE_VERIFICACAO_INVALIDA} se a
   *     tentativa já foi concluída ou apagada; {@code CONTA_INDISPONIVEL} se a conta não existe. O
   *     chamador desfaz a transação
   * @throws IllegalStateException sem transação ativa
   */
  void concluir(
      Tentativa tentativa,
      Conta conta,
      ResultadoDaVerificacao resultado,
      MetodoDeVerificacao metodo,
      Instant agora) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()) {
      throw new IllegalStateException("Os efeitos da verificação exigem a transação do chamador");
    }
    if (resultado == ResultadoDaVerificacao.RECUSADA) {
      limpeza.travarEmail(emailDe(conta));
    }
    Conta travada =
        contas
            .travar(conta.id())
            .orElseThrow(() ->
                new ErroDeNegocio(
                    CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem()));
    if (mudaAConta(resultado)) {
      VerificacaoDeIdade.exigirAguardando(travada);
    }
    if (!verificacoes.concluir(tentativa.id(), resultado, agora)) {
      throw CodigoVerificacao.SESSAO_DE_VERIFICACAO_INVALIDA.erro();
    }
    switch (resultado) {
      case APROVADA -> {
        if (!contas.ativar(travada.id(), agora)) {
          throw CodigoVerificacao.VERIFICACAO_FORA_DE_ORDEM.erro();
        }
        verificacoes.gravarAprovacao(travada.id(), metodo, agora);
      }
      case RECUSADA ->
          limpeza.excluirEBloquear(travada.id(), emailDe(conta), MOTIVO_DO_BLOQUEIO, agora);
      case DUVIDA, REPETIR -> {
        // A conta segue aguardando; só a tentativa guarda o resultado.
      }
    }
  }

  private static boolean mudaAConta(ResultadoDaVerificacao resultado) {
    return resultado == ResultadoDaVerificacao.APROVADA
        || resultado == ResultadoDaVerificacao.RECUSADA;
  }

  /** A conta aguardando verificação sempre tem e-mail: a invariante da {@link Conta} garante. */
  private static Email emailDe(Conta conta) {
    return Objects.requireNonNull(conta.email(), "Conta aguardando verificação sem e-mail");
  }
}
