package bipotheking.net.duora.perfil;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.perfil.dominio.RepositorioDePausa;
import java.time.Clock;
import org.springframework.stereotype.Service;

/**
 * Tira a pessoa da fila de encontros e a devolve, sem mexer na conta nem na versão do perfil.
 * Pausar de novo mantém o instante da primeira pausa.
 */
@Service
public class PausaDaConta {

  private final RepositorioDePausa pausas;
  private final Clock relogio;

  PausaDaConta(RepositorioDePausa pausas, Clock relogio) {
    this.pausas = pausas;
    this.relogio = relogio;
  }

  /**
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code PERFIL_NECESSARIO} sem perfil
   */
  public void pausar(ContaId conta) {
    if (!pausas.pausar(conta, relogio.instant())) {
      throw CodigoPerfil.PERFIL_NECESSARIO.erro();
    }
  }

  /**
   * @throws bipotheking.net.duora.nucleo.ErroDeNegocio {@code PERFIL_NECESSARIO} sem perfil
   */
  public void retomar(ContaId conta) {
    if (!pausas.retomar(conta)) {
      throw CodigoPerfil.PERFIL_NECESSARIO.erro();
    }
  }

  public boolean pausada(ContaId conta) {
    return pausas.pausada(conta);
  }
}
