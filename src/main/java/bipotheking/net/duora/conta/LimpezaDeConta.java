package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.notificacao.Outbox;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * Apaga os dados pessoais da conta e a deixa como lápide anônima, na transação do chamador.
 *
 * <p>A trava do e-mail é a mesma do pedido de link, que a toma antes de checar o bloqueio: um
 * pedido que já travou termina antes, e o link e a mensagem dele entram na limpeza; um pedido que
 * chega depois espera o commit. Os links saem antes das sessões: uma confirmação que consumiu um
 * link em paralelo segura a linha dele, então a limpeza espera o commit dela e revoga também a
 * sessão que ela abriu. A exclusão vem antes de apagar os aceites: se um cadastro concorrente da
 * mesma conta gravou aceites, a trava da linha da conta faz esta transação esperar o commit dele e
 * vê-los.
 */
@Component
class LimpezaDeConta {

  private final RepositorioDeContas contas;
  private final RepositorioDeAceites aceites;
  private final RepositorioDeBloqueios bloqueios;
  private final RepositorioDeLinks links;
  private final ResumoDeEmail resumoDeEmail;
  private final Outbox outbox;
  private final Sessoes sessoes;

  LimpezaDeConta(
      RepositorioDeContas contas,
      RepositorioDeAceites aceites,
      RepositorioDeBloqueios bloqueios,
      RepositorioDeLinks links,
      ResumoDeEmail resumoDeEmail,
      Outbox outbox,
      Sessoes sessoes) {
    this.contas = contas;
    this.aceites = aceites;
    this.bloqueios = bloqueios;
    this.links = links;
    this.resumoDeEmail = resumoDeEmail;
    this.outbox = outbox;
    this.sessoes = sessoes;
  }

  /**
   * Trava o e-mail, bloqueia-o e exclui a conta: depois do commit, um pedido de link do mesmo
   * e-mail vê o bloqueio e não grava link.
   */
  void excluirEBloquear(ContaId id, Email email, String motivo, Instant agora) {
    String titular = travar(email);
    bloqueios.bloquear(resumoDeEmail.de(email), motivo, agora);
    apagar(id, email, titular, agora);
  }

  private String travar(Email email) {
    String titular = resumoDeEmail.hex(email);
    links.travarEmail(titular);
    return titular;
  }

  /**
   * Apaga os links do e-mail (nenhuma linha guarda o e-mail), a carga das mensagens pendentes (o
   * link pendente não sai), as sessões, os dados da conta e os aceites. Só com a trava tomada.
   */
  private void apagar(ContaId id, Email email, String titular, Instant agora) {
    links.apagarDoEmail(email);
    outbox.esquecerTitular(titular);
    sessoes.revogarTodas(id);
    contas.excluir(id, agora);
    aceites.apagarDaConta(id);
  }
}
