package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.ContaId;
import java.util.List;

/**
 * O que um módulo manda quando o stream da conta abre e não há como reenviar o que ela perdeu: o
 * estado atual, como efêmeros (sem {@code id}).
 */
public interface EstadoInicial {

  /** Os eventos do estado atual da conta; vazio se o módulo não tem nada a dizer. */
  List<Evento> para(ContaId conta);
}
