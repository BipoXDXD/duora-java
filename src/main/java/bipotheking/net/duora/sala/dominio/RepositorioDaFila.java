package bipotheking.net.duora.sala.dominio;

import bipotheking.net.duora.conta.ContaId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** A fila de espera (tabela {@code fila_espera}, V53). */
public interface RepositorioDaFila {

  /** Põe a conta na fila; se ela já está, nada muda e devolve {@code false}. */
  boolean inserir(ContaId conta, Instant entrouEm, boolean deTeste);

  Optional<EntradaNaFila> daConta(ContaId conta);

  /** Tira a conta da fila; devolve {@code false} se ela não estava. */
  boolean remover(ContaId conta);

  /**
   * A posição da conta entre quem ainda espera, por ordem de chegada (empate pela conta), a partir
   * de 1; 0 se ela não está esperando.
   */
  int posicaoDe(ContaId conta);

  /** Quem ainda espera e entrou até {@code limite}, por ordem de chegada. */
  List<EntradaNaFila> esperandoDesde(Instant limite);
}
