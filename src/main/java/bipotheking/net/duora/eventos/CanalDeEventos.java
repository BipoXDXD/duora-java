package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.dominio.RepositorioDeEventos;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Por onde os módulos mandam eventos de tempo real às contas. O durável é gravado na transação de
 * quem publica, com a trava da conta segurada até o commit (o {@code seq} de cada conta cresce na
 * ordem do commit), e os streams só são acordados depois do commit: um rollback não grava nem avisa
 * ninguém. O efêmero vai direto aos streams abertos, sem gravar.
 */
@Component
public class CanalDeEventos {

  private static final Logger LOG = LoggerFactory.getLogger(CanalDeEventos.class);

  /**
   * A ordem do {@code uuid} no PostgreSQL: os bytes sem sinal, da esquerda para a direita. Toda
   * transação trava as contas nesta ordem, e duas publicações com as mesmas contas não se travam
   * mutuamente.
   */
  private static final Comparator<ContaId> ORDEM_DO_BANCO =
      Comparator.<ContaId, Long>comparing(
              conta -> conta.valor().getMostSignificantBits(), Long::compareUnsigned)
          .thenComparing(conta -> conta.valor().getLeastSignificantBits(), Long::compareUnsigned);

  private final RepositorioDeEventos repositorio;
  private final EntregaDeEventos entrega;
  private final Clock relogio;

  CanalDeEventos(RepositorioDeEventos repositorio, EntregaDeEventos entrega, Clock relogio) {
    this.repositorio = repositorio;
    this.entrega = entrega;
    this.relogio = relogio;
  }

  /**
   * Grava os eventos na transação corrente, na ordem da lista, depois de travar cada conta em ordem
   * crescente; no commit, acorda cada conta uma vez. Lista vazia não faz nada.
   *
   * @throws org.springframework.transaction.IllegalTransactionStateException sem transação
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void publicar(List<Envio> envios) {
    if (envios.isEmpty()) {
      return;
    }
    SortedSet<ContaId> contas = new TreeSet<>(ORDEM_DO_BANCO);
    envios.forEach(envio -> contas.add(envio.para()));
    contas.forEach(repositorio::travarConta);
    Instant agora = relogio.instant();
    envios.forEach(envio -> repositorio.gravar(envio.para(), envio.evento(), agora));
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            contas.forEach(CanalDeEventos.this::acordar);
          }
        });
  }

  /**
   * Um evento só, como {@link #publicar(List)}.
   *
   * @throws org.springframework.transaction.IllegalTransactionStateException sem transação
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void publicar(ContaId conta, Evento evento) {
    publicar(List.of(new Envio(conta, evento)));
  }

  /** Manda o evento aos streams abertos da conta agora, sem gravar e sem {@code id}. */
  public void publicarEfemero(ContaId conta, Evento evento) {
    entrega.efemero(conta, evento);
  }

  /** Se a conta tem algum stream aberto nesta instância. */
  public boolean conectada(ContaId conta) {
    return entrega.conectada(conta);
  }

  /** Fecha os streams abertos da conta. */
  public void encerrarStreams(ContaId conta) {
    entrega.encerrar(conta);
  }

  /** Depois do commit o evento já está gravado: uma falha aqui só atrasa a entrega. */
  private void acordar(ContaId conta) {
    try {
      entrega.acordar(conta);
    } catch (RuntimeException e) {
      LOG.warn("Falha ao acordar os streams da conta {}", conta, e);
    }
  }
}
