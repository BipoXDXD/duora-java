package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeAceites;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.conta.dominio.RepositorioDeVerificacoes;
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
 * sessão que ela abriu. A exclusão vem antes de apagar os aceites e a verificação de idade: se um
 * cadastro ou uma reserva de tentativa concorrente da mesma conta gravou linhas, a trava da linha
 * da conta faz esta transação esperar o commit dele e vê-las.
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
  private final RepositorioDeVerificacoes verificacoes;

  LimpezaDeConta(
      RepositorioDeContas contas,
      RepositorioDeAceites aceites,
      RepositorioDeBloqueios bloqueios,
      RepositorioDeLinks links,
      ResumoDeEmail resumoDeEmail,
      Outbox outbox,
      Sessoes sessoes,
      RepositorioDeVerificacoes verificacoes) {
    this.contas = contas;
    this.aceites = aceites;
    this.bloqueios = bloqueios;
    this.links = links;
    this.resumoDeEmail = resumoDeEmail;
    this.outbox = outbox;
    this.sessoes = sessoes;
    this.verificacoes = verificacoes;
  }

  /**
   * Trava o e-mail, bloqueia-o e exclui a conta: depois do commit, um pedido de link do mesmo
   * e-mail vê o bloqueio e não grava link. O bloqueio vale mesmo se a conta já saiu dos estados
   * disponíveis numa transação concorrente.
   */
  void excluirEBloquear(ContaId id, Email email, String motivo, Instant agora) {
    String titular = travar(email);
    bloqueios.bloquear(resumoDeEmail.de(email), motivo, agora);
    apagar(id, email, titular, agora);
  }

  /**
   * Trava o e-mail e exclui a conta, sem bloquear o e-mail: um pedido de link que esperava a trava
   * grava, depois do commit, um link para uma conta nova, sem nada da excluída.
   *
   * @return se a conta ainda estava num estado disponível e foi excluída; se não, o chamador decide
   *     o que desfazer
   */
  boolean excluir(ContaId id, Email email, Instant agora) {
    String titular = travar(email);
    return apagar(id, email, titular, agora);
  }

  /**
   * Toma a trava do e-mail sem apagar nada, para quem precisa dela antes das outras (a ordem das
   * travas é e-mail, conta, tentativas). A trava é de transação e reentrante: a exclusão que vem
   * depois, na mesma transação, a toma de novo sem esperar.
   */
  void travarEmail(Email email) {
    travar(email);
  }

  private String travar(Email email) {
    String titular = resumoDeEmail.hex(email);
    links.travarEmail(titular);
    return titular;
  }

  /**
   * Apaga os links do e-mail (nenhuma linha guarda o e-mail), a carga das mensagens pendentes (o
   * link pendente não sai), as sessões, os dados da conta, os aceites e a verificação de idade
   * (tentativas e aprovação). Só com a trava tomada. A linha da conta é a última trava: a exclusão
   * condicional espera quem a mudou em paralelo e vê o estado dele. Aceites e verificação só saem
   * se a conta foi excluída.
   *
   * @return se a conta estava num estado disponível e foi excluída
   */
  private boolean apagar(ContaId id, Email email, String titular, Instant agora) {
    links.apagarDoEmail(email);
    outbox.esquecerTitular(titular);
    sessoes.revogarTodas(id);
    if (!contas.excluir(id, agora)) {
      return false;
    }
    aceites.apagarDaConta(id);
    verificacoes.apagarDaConta(id);
    return true;
  }
}
