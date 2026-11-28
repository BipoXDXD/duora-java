package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.AoExcluirConta;
import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Apaga os eventos na transação da exclusão da conta e fecha os streams dela depois do commit: um
 * rollback deixa a conta conectada. A conta fica como lápide (a linha não sai), então o {@code on
 * delete cascade} da chave estrangeira nunca dispara sozinho.
 */
@Component
class EventosAoExcluirConta implements AoExcluirConta {

  private static final Logger LOG = LoggerFactory.getLogger(EventosAoExcluirConta.class);

  private final RepositorioDeEventos repositorio;
  private final EntregaDeEventos entrega;

  EventosAoExcluirConta(RepositorioDeEventos repositorio, EntregaDeEventos entrega) {
    this.repositorio = repositorio;
    this.entrega = entrega;
  }

  @Override
  public void excluir(ContaId conta) {
    repositorio.apagarDaConta(conta);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            encerrar(conta);
          }
        });
  }

  /** A exclusão já foi gravada: uma falha aqui deixa o stream aberto até ele vencer. */
  private void encerrar(ContaId conta) {
    try {
      entrega.encerrar(conta);
    } catch (RuntimeException e) {
      LOG.warn("Falha ao encerrar os streams da conta {} excluída", conta, e);
    }
  }
}
