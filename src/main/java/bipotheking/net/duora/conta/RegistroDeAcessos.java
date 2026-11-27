package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.RepositorioDeAcessos;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.Origem;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registros de acesso do Marco Civil (art. 15): IP, porta de origem e instante de cada login e
 * renovação de sessão, guardados por 183 dias. O IP é dado pessoal: esta classe nunca loga, e o
 * registro não tem API de leitura.
 */
@Service
class RegistroDeAcessos {

  private final RepositorioDeAcessos repositorio;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;

  RegistroDeAcessos(RepositorioDeAcessos repositorio, Clock relogio, GeradorDeIds geradorDeIds) {
    this.repositorio = repositorio;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
  }

  /**
   * Grava o acesso agora, na transação do chamador: o registro só existe se o fato existir.
   *
   * @throws org.springframework.transaction.IllegalTransactionStateException fora de transação
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void registrar(Optional<ContaId> conta, Origem origem, EventoDeAcesso evento) {
    repositorio.gravar(geradorDeIds.novo(), conta, origem, relogio.instant(), evento);
  }
}
