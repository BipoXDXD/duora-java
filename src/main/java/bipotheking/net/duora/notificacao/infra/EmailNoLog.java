package bipotheking.net.duora.notificacao.infra;

import bipotheking.net.duora.notificacao.EnvioDeEmail;
import bipotheking.net.duora.notificacao.MensagemDeEmail;
import bipotheking.net.duora.nucleo.EmailMascarado;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envio de desenvolvimento e teste: escreve o e-mail no log em vez de entregá-lo e guarda os 50
 * últimos em memória. O destinatário sai mascarado; o texto sai inteiro, com o link de entrada
 * (exceção registrada nas Global Constraints). Nunca roda em prod: o {@link EmailConfig} recusa.
 *
 * <p>Pública porque os testes de conta e o e2e leem o link por {@link #ultimaPara(String)}.
 */
public final class EmailNoLog implements EnvioDeEmail {

  private static final Logger LOG = LoggerFactory.getLogger(EmailNoLog.class);

  private static final int CAPACIDADE = 50;

  /** Da mais antiga para a mais recente; guardada pelo monitor dela mesma. */
  private final Deque<MensagemDeEmail> ultimas = new ArrayDeque<>(CAPACIDADE);

  @Override
  public void enviar(MensagemDeEmail mensagem, UUID chaveDeIdempotencia) {
    LOG.info(
        "E-mail {} para {} ({}):\n{}",
        chaveDeIdempotencia,
        EmailMascarado.mascarar(mensagem.para()),
        mensagem.assunto(),
        mensagem.texto());
    synchronized (ultimas) {
      if (ultimas.size() == CAPACIDADE) {
        ultimas.removeFirst();
      }
      ultimas.addLast(mensagem);
    }
  }

  /** O e-mail mais recente para o endereço, entre os 50 últimos, sem diferenciar maiúsculas. */
  public Optional<MensagemDeEmail> ultimaPara(String email) {
    synchronized (ultimas) {
      Iterator<MensagemDeEmail> daMaisRecente = ultimas.descendingIterator();
      while (daMaisRecente.hasNext()) {
        MensagemDeEmail mensagem = daMaisRecente.next();
        if (mensagem.para().equalsIgnoreCase(email)) {
          return Optional.of(mensagem);
        }
      }
      return Optional.empty();
    }
  }
}
