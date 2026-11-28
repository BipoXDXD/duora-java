package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.AoEncerrarSessoes;
import bipotheking.net.duora.conta.ContaId;
import org.springframework.stereotype.Component;

/**
 * Fecha os streams da conta no logout e na revogação: depois de sair, nada mais chega à aba aberta.
 * Os streams são por conta, então o logout num aparelho fecha também os dos outros, que reconectam
 * com a própria sessão.
 */
@Component
class EventosAoEncerrarSessoes implements AoEncerrarSessoes {

  private final EntregaDeEventos entrega;

  EventosAoEncerrarSessoes(EntregaDeEventos entrega) {
    this.entrega = entrega;
  }

  @Override
  public void encerradas(ContaId conta) {
    entrega.encerrar(conta);
  }
}
