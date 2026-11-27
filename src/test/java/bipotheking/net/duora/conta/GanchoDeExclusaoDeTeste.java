package bipotheking.net.duora.conta;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gancho de exclusão que registra as chamadas, para os testes verem se a exclusão o chama e na
 * transação dela. {@link #naProxima} faz a próxima chamada rodar uma ação no meio da exclusão;
 * {@link #falharNaProxima()} faz a próxima chamada gravar e lançar, como um módulo que falha depois
 * de mexer no banco.
 */
@Component
class GanchoDeExclusaoDeTeste implements AoExcluirConta {

  /** Uma chamada do gancho: a conta e se havia transação aberta. */
  record Chamada(ContaId conta, boolean emTransacao) {}

  private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
  private final AtomicReference<Consumer<ContaId>> proxima = new AtomicReference<>();
  private final Sessoes sessoes;

  GanchoDeExclusaoDeTeste(Sessoes sessoes) {
    this.sessoes = sessoes;
  }

  @Override
  public void excluir(ContaId conta) {
    chamadas.add(new Chamada(conta, TransactionSynchronizationManager.isActualTransactionActive()));
    Consumer<ContaId> acao = proxima.getAndSet(null);
    if (acao != null) {
      acao.accept(conta);
    }
  }

  /** A próxima chamada roda a ação, na transação da exclusão. */
  void naProxima(Consumer<ContaId> acao) {
    proxima.set(acao);
  }

  /**
   * A próxima chamada revoga as sessões da conta e lança: se as sessões sobrevivem, a exclusão
   * desfez o que o gancho gravou.
   */
  void falharNaProxima() {
    naProxima(conta -> {
      sessoes.revogarTodas(conta);
      throw new IllegalStateException("Falha simulada no gancho de exclusão");
    });
  }

  List<Chamada> chamadas() {
    return List.copyOf(chamadas);
  }

  /** Esquece as chamadas e a ação pendente: o contexto é dividido entre os testes. */
  void esquecer() {
    chamadas.clear();
    proxima.set(null);
  }
}
