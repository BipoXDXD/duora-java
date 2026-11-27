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
   * Exclui a conta. Uma falha num gancho desfaz tudo. A conta já excluída (dois envios simultâneos)
   * não muda de novo.
   *
   * @throws ErroDeNegocio {@code CONFIRMACAO_NECESSARIA} sem confirmação; {@code
   *     CONTA_INDISPONIVEL} se a conta foi suspensa ou banida depois da checagem da sessão: excluir
   *     a banida liberaria o e-mail para uma conta nova
   */
  public void excluir(ContaId conta, boolean confirmo) {
    if (!confirmo) {
      throw CodigoExclusao.CONFIRMACAO_NECESSARIA.erro();
    }
    transacao.executeWithoutResult(status -> efetuar(conta));
  }

  private void efetuar(ContaId id) {
    Conta conta =
        contas
            .porId(id)
            .orElseThrow(() -> new IllegalStateException("Sessão de conta inexistente: " + id));
    if (conta.estado() == EstadoConta.EXCLUIDA) {
      LOG.info("Conta {} já estava excluída", id);
      return;
    }
    if (!conta.estado().disponivel()) {
      throw new ErroDeNegocio(
          CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem());
    }
    ganchos.orderedStream().forEach(gancho -> gancho.excluir(id));
    limpeza.excluir(id, emailDe(conta), relogio.instant());
    LOG.info("Conta {} excluída a pedido da pessoa", id);
  }

  /** A conta disponível sempre tem e-mail: a invariante da {@link Conta} garante. */
  private static Email emailDe(Conta conta) {
    return Objects.requireNonNull(conta.email(), "Conta disponível sem e-mail");
  }
}
