package bipotheking.net.duora.eventos.dominio;

import bipotheking.net.duora.conta.ContaId;
import bipotheking.net.duora.eventos.Evento;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

/** Os eventos gravados de cada conta (tabela {@code evento_conta}, V50). */
public interface RepositorioDeEventos {

  /**
   * Trava consultiva da conta até o fim da transação: quem grava para a mesma conta espera o
   * commit, e o {@code seq} de cada conta cresce na ordem do commit. Exige transação.
   */
  void travarConta(ContaId conta);

  /**
   * Grava o evento na transação corrente.
   *
   * @return o {@code seq} do evento
   */
  long gravar(ContaId conta, Evento evento, Instant agora);

  /** Até {@code limite} eventos da conta com {@code seq} maior que o dado, em ordem crescente. */
  List<EventoGravado> depoisDe(ContaId conta, long seq, int limite);

  /** O menor {@code seq} ainda guardado da conta; vazio se ela não tem eventos. */
  OptionalLong menorSeq(ContaId conta);

  /**
   * Apaga todos os eventos da conta.
   *
   * @return quantos apagou
   */
  int apagarDaConta(ContaId conta);
}
