package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import java.time.Clock;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Exclusão da conta pela própria pessoa. Numa transação, os ganchos dos outros módulos rodam
 * primeiro, na ordem do {@code @Order}; depois a {@link LimpezaDeConta} apaga os links, a carga das
 * mensagens pendentes, as sessões, os dados pessoais e os aceites, e deixa a conta como lápide
 * anônima. O e-mail não é bloqueado: a mesma pessoa pode criar uma conta nova.
 */
@Service
public class ExclusaoDeConta {

  private static final Logger LOG = LoggerFactory.getLogger(ExclusaoDeConta.class);

  private final RepositorioDeContas contas;
  private final LimpezaDeConta limpeza;
  private final ObjectProvider<AoExcluirConta> ganchos;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  ExclusaoDeConta(
      RepositorioDeContas contas,
      LimpezaDeConta limpeza,
      ObjectProvider<AoExcluirConta> ganchos,
      TransactionTemplate transacao,
      Clock relogio) {
    this.contas = contas;
    this.limpeza = limpeza;
    this.ganchos = ganchos;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * Exclui a conta. Uma falha num gancho desfaz tudo. A leitura inicial não trava a linha: a
   * decisão final é do UPDATE condicional da {@link LimpezaDeConta}, que espera quem mudou a conta
   * em paralelo. Se outro envio a excluiu antes, esta transação desfaz o que fez (os ganchos
   * repetidos) e termina sem erro.
   *
   * @throws ErroDeNegocio {@code CONFIRMACAO_NECESSARIA} sem confirmação; {@code
   *     CONTA_INDISPONIVEL} se a conta foi suspensa ou banida depois da checagem da sessão, mesmo
   *     no meio da exclusão: excluí-la liberaria o e-mail para uma conta nova
   */
  public void excluir(ContaId conta, boolean confirmo) {
    if (!confirmo) {
      throw CodigoExclusao.CONFIRMACAO_NECESSARIA.erro();
    }
    transacao.executeWithoutResult(status -> efetuar(conta, status));
  }

  private void efetuar(ContaId id, TransactionStatus status) {
    Conta conta = conta(id);
    if (conta.estado() == EstadoConta.EXCLUIDA) {
      LOG.info("Conta {} já estava excluída", id);
      return;
    }
    if (!conta.estado().disponivel()) {
      throw indisponivel();
    }
    ganchos.orderedStream().forEach(gancho -> gancho.excluir(id));
    if (limpeza.excluir(id, emailDe(conta), relogio.instant())) {
      LOG.info("Conta {} excluída a pedido da pessoa", id);
      return;
    }
    // O UPDATE esperou o commit concorrente; esta leitura, um comando novo, já o vê.
    if (conta(id).estado() == EstadoConta.EXCLUIDA) {
      status.setRollbackOnly();
      LOG.info("Conta {} excluída por outro envio; esta exclusão foi desfeita", id);
      return;
    }
    throw indisponivel();
  }

  private Conta conta(ContaId id) {
    return contas
        .porId(id)
        .orElseThrow(() -> new IllegalStateException("Sessão de conta inexistente: " + id));
  }

  private static ErroDeNegocio indisponivel() {
    return new ErroDeNegocio(
        CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem());
  }

  /** A conta disponível sempre tem e-mail: a invariante da {@link Conta} garante. */
  private static Email emailDe(Conta conta) {
    return Objects.requireNonNull(conta.email(), "Conta disponível sem e-mail");
  }
}
