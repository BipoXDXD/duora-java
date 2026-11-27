package bipotheking.net.duora.conta;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Gancho de exclusão que só registra as chamadas, para os testes verem se a exclusão o chama e na
 * transação dela. {@link #falharNaProxima()} faz a próxima chamada lançar, como um módulo que falha
 * no meio da exclusão.
 */
@Component
public class GanchoDeExclusaoDeTeste implements AoExcluirConta {

  /** Uma chamada do gancho: a conta e se havia transação aberta. */
  public record Chamada(ContaId conta, boolean emTransacao) {}

  private final List<Chamada> chamadas = new CopyOnWriteArrayList<>();
  private final AtomicBoolean falhar = new AtomicBoolean();

  @Override
  public void excluir(ContaId conta) {
    chamadas.add(new Chamada(conta, TransactionSynchronizationManager.isActualTransactionActive()));
    if (falhar.getAndSet(false)) {
      throw new IllegalStateException("Falha simulada no gancho de exclusão");
    }
  }

  /** A próxima chamada lança depois de registrar. */
  public void falharNaProxima() {
    falhar.set(true);
  }

  public List<Chamada> chamadas() {
    return List.copyOf(chamadas);
  }

  /** Esquece as chamadas e a falha pendente: o contexto é dividido entre os testes. */
  public void esquecer() {
    chamadas.clear();
    falhar.set(false);
  }
}
