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
 * {@link #falharNaProxima()} faz a próxima chamada lançar, como um módulo que falha.
 */
@Component
public class GanchoDeExclusaoDeTeste implements AoExcluirConta {

  /** Uma chamada do gancho: a conta e se havia transação aberta. */
  public record Chamada(ContaId conta, boolean emTransacao) {}

  private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
  private final AtomicReference<Consumer<ContaId>> proxima = new AtomicReference<>();

  @Override
  public void excluir(ContaId conta) {
    chamadas.add(new Chamada(conta, TransactionSynchronizationManager.isActualTransactionActive()));
    Consumer<ContaId> acao = proxima.getAndSet(null);
    if (acao != null) {
      acao.accept(conta);
    }
  }

  /** A próxima chamada roda a ação, na transação da exclusão. */
  public void naProxima(Consumer<ContaId> acao) {
    proxima.set(acao);
  }

  /** A próxima chamada lança. */
  public void falharNaProxima() {
    naProxima(conta -> {
      throw new IllegalStateException("Falha simulada no gancho de exclusão");
    });
  }

  public List<Chamada> chamadas() {
    return List.copyOf(chamadas);
  }

  /** Esquece as chamadas e a ação pendente: o contexto é dividido entre os testes. */
  public void esquecer() {
    chamadas.clear();
    proxima.set(null);
  }
}
