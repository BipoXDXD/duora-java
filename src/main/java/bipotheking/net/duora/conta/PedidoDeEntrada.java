package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.DominiosDescartaveis;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.conta.dominio.VerificacaoHumana;
import bipotheking.net.duora.notificacao.Outbox;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.Idempotencia;
import bipotheking.net.duora.web.Idempotencia.Reserva;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pedido de link mágico. A resposta é a mesma exista ou não a conta, esteja ou não o e-mail
 * bloqueado. O link e o e-mail entram na outbox na mesma transação; o Turnstile roda antes, fora
 * dela. Com chave de idempotência, a chave é reservada antes do Turnstile: a repetição devolve a
 * resposta gravada sem novo efeito nem nova verificação.
 */
@Service
public class PedidoDeEntrada {

  static final String ESCOPO_DE_IDEMPOTENCIA = "entrar";
  private static final String LIMITE_POR_IP = "entrar-ip";
  private static final String LIMITE_POR_EMAIL = "entrar-email";
  private static final Duration VALIDADE_DO_LINK = Duration.ofMinutes(15);
  private static final RespostaDeEntrada RESPOSTA =
      new RespostaDeEntrada("Enviamos um link para o seu e-mail. Ele vale por 15 minutos.");

  private static final Logger LOG = LoggerFactory.getLogger(PedidoDeEntrada.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final Idempotencia idempotencia;
  private final VerificacaoHumana verificacaoHumana;
  private final DominiosDescartaveis descartaveis;
  private final RepositorioDeLinks links;
  private final RepositorioDeBloqueios bloqueios;
  private final ResumoDeEmail resumoDeEmail;
  private final Outbox outbox;
  private final MensagensDeEntrada mensagens;
  private final TransactionTemplate transacao;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;
  private final RandomGenerator aleatorio;

  PedidoDeEntrada(
      LimiteDeTaxa limiteDeTaxa,
      Idempotencia idempotencia,
      VerificacaoHumana verificacaoHumana,
      DominiosDescartaveis descartaveis,
      RepositorioDeLinks links,
      RepositorioDeBloqueios bloqueios,
      ResumoDeEmail resumoDeEmail,
      Outbox outbox,
      MensagensDeEntrada mensagens,
      TransactionTemplate transacao,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      RandomGenerator aleatorio) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.idempotencia = idempotencia;
    this.verificacaoHumana = verificacaoHumana;
    this.descartaveis = descartaveis;
    this.links = links;
    this.bloqueios = bloqueios;
    this.resumoDeEmail = resumoDeEmail;
    this.outbox = outbox;
    this.mensagens = mensagens;
    this.transacao = transacao;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.aleatorio = aleatorio;
  }

  /** O que o front mostra depois do pedido, igual para todo e-mail aceito. */
  public record RespostaDeEntrada(String mensagem) {}

  /**
   * Pede um link de entrada para o e-mail.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code EMAIL_INVALIDO}, {@code
   *     EMAIL_DESCARTAVEL}, {@code VERIFICACAO_HUMANA_FALHOU} ou um dos erros da {@link
   *     Idempotencia#reservar}
   */
  public RespostaDeEntrada pedir(
      @Nullable String emailBruto,
      @Nullable String tokenVerificacao,
      String ip,
      Optional<String> chaveDeIdempotencia) {
    limiteDeTaxa.consumir(LIMITE_POR_IP, ip);
    Email email = Email.de(emailBruto);
    if (chaveDeIdempotencia.isEmpty()) {
      return efetuar(email, tokenVerificacao, ip, null);
    }
    String chave = chaveDeIdempotencia.get();
    Reserva<RespostaDeEntrada> reserva =
        idempotencia.reservar(
            ESCOPO_DE_IDEMPOTENCIA,
            chave,
            Resumos.sha256Hex(email.valor()),
            RespostaDeEntrada.class);
    return switch (reserva) {
      case Reserva.JaRespondida<RespostaDeEntrada>(RespostaDeEntrada resposta) -> resposta;
      case Reserva.Tua<RespostaDeEntrada>(UUID dono) ->
          efetuarComChave(email, tokenVerificacao, ip, new ChaveReservada(chave, dono));
    };
  }

  /**
   * Grava o link novo, depois de invalidar os abertos do e-mail, e registra o e-mail com o link na
   * outbox. Precisa de uma transação aberta: separado de {@link #pedir} para o teste de
   * atomicidade.
   */
  void gravarLinkEMensagem(Email email, Token token) {
    Instant agora = relogio.instant();
    links.invalidarAbertos(email, agora);
    links.gravar(geradorDeIds.novo(), email, token.resumo(), agora, agora.plus(VALIDADE_DO_LINK));
    outbox.registrar(Outbox.EMAIL, resumoDeEmail.hex(email), mensagens.link(email, token.valor()));
  }

  /** Qualquer falha depois da reserva libera a chave, para a mesma chave tentar de novo. */
  private RespostaDeEntrada efetuarComChave(
      Email email, @Nullable String tokenVerificacao, String ip, ChaveReservada chave) {
    try {
      return efetuar(email, tokenVerificacao, ip, chave);
    } catch (RuntimeException erro) {
      try {
        idempotencia.liberar(ESCOPO_DE_IDEMPOTENCIA, chave.chave(), chave.dono());
      } catch (RuntimeException naLiberacao) {
        erro.addSuppressed(naLiberacao);
      }
      throw erro;
    }
  }

  private RespostaDeEntrada efetuar(
      Email email, @Nullable String tokenVerificacao, String ip, @Nullable ChaveReservada chave) {
    limiteDeTaxa.consumir(LIMITE_POR_EMAIL, email.chaveDeBloqueio());
    if (!verificacaoHumana.verificar(tokenVerificacao, ip)) {
      throw new ErroDeNegocio(
          CodigoVerificacaoHumana.VERIFICACAO_HUMANA_FALHOU,
          "Não foi possível confirmar que você é uma pessoa. Tente de novo.");
    }
    if (descartaveis.contem(email.dominio())) {
      throw new ErroDeNegocio(CodigoEmail.EMAIL_DESCARTAVEL, "Use um e-mail permanente.");
    }
    transacao.executeWithoutResult(status -> {
      if (bloqueios.bloqueado(resumoDeEmail.de(email))) {
        outbox.registrar(Outbox.EMAIL, resumoDeEmail.hex(email), mensagens.avisoDeBloqueio(email));
      } else {
        gravarLinkEMensagem(email, Token.novo(aleatorio));
      }
      if (chave != null) {
        idempotencia.concluir(ESCOPO_DE_IDEMPOTENCIA, chave.chave(), chave.dono(), RESPOSTA);
      }
    });
    LOG.info("Pedido de entrada registrado para {}", email);
    return RESPOSTA;
  }

  /** A chave de idempotência e o dono da reserva, que conclui ou libera. */
  private record ChaveReservada(String chave, UUID dono) {}
}
