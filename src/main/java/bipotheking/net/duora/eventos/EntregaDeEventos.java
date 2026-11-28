package bipotheking.net.duora.eventos;

import bipotheking.net.duora.conta.ContaId;

/**
 * Quem leva os eventos aos streams abertos. Pública só para {@code eventos.web} e os testes: os
 * outros módulos publicam pelo {@link CanalDeEventos}. Nenhum método lança; uma conta sem stream
 * aberto é ignorada.
 */
public interface EntregaDeEventos {

  /** Há eventos gravados novos para a conta: os streams dela leem a partir do último enviado. */
  void acordar(ContaId conta);

  /** Manda o evento aos streams abertos da conta, sem gravar e sem {@code id}. */
  void efemero(ContaId conta, Evento evento);

  /** Se a conta tem algum stream aberto nesta instância. */
  boolean conectada(ContaId conta);

  /** Fecha os streams abertos da conta. */
  void encerrar(ContaId conta);
}
