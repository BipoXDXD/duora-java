package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.Conta;
import bipotheking.net.duora.conta.dominio.Email;
import bipotheking.net.duora.conta.dominio.RepositorioDeBloqueios;
import bipotheking.net.duora.conta.dominio.RepositorioDeContas;
import bipotheking.net.duora.conta.dominio.RepositorioDeLinks;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.Resumos;
import bipotheking.net.duora.web.LimiteDeTaxa;
import java.time.Clock;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Confirmação do link mágico: consome o link, acha ou cria a conta do e-mail e abre a sessão, tudo
 * numa transação. Uma recusa depois do consumo desfaz o consumo: o link continua valendo.
 */
@Service
public class ConfirmacaoDeEntrada {

  private static final String LIMITE_POR_IP = "confirmar-ip";

  /** O token tem 43 caracteres; um valor acima disto nem chega ao banco. */
  private static final int TAMANHO_MAXIMO_DO_TOKEN = 64;

  private static final Logger LOG = LoggerFactory.getLogger(ConfirmacaoDeEntrada.class);

  private final LimiteDeTaxa limiteDeTaxa;
  private final RepositorioDeLinks links;
  private final RepositorioDeBloqueios bloqueios;
  private final ResumoDeEmail resumoDeEmail;
  private final RepositorioDeContas contas;
  private final Sessoes sessoes;
  private final TransactionTemplate transacao;
  private final Clock relogio;

  ConfirmacaoDeEntrada(
      LimiteDeTaxa limiteDeTaxa,
      RepositorioDeLinks links,
      RepositorioDeBloqueios bloqueios,
      ResumoDeEmail resumoDeEmail,
      RepositorioDeContas contas,
      Sessoes sessoes,
      TransactionTemplate transacao,
      Clock relogio) {
    this.limiteDeTaxa = limiteDeTaxa;
    this.links = links;
    this.bloqueios = bloqueios;
    this.resumoDeEmail = resumoDeEmail;
    this.contas = contas;
    this.sessoes = sessoes;
    this.transacao = transacao;
    this.relogio = relogio;
  }

  /**
   * O token da sessão nova, que vai só para o cookie, e se a conta já concluiu o cadastro. O {@code
   * toString} não mostra o token.
   */
  public record ResultadoDaConfirmacao(String tokenDaSessao, boolean cadastroConcluido) {

    @Override
    public String toString() {
      return "ResultadoDaConfirmacao[tokenDaSessao=***, cadastroConcluido="
          + cadastroConcluido
          + "]";
    }
  }

  /**
   * Confirma o link e abre uma sessão, encerrando a anterior do mesmo navegador.
   *
   * @throws ErroDeNegocio {@code LIMITE_DE_TAXA}, {@code LINK_INVALIDO} ou {@code
   *     CONTA_INDISPONIVEL}
   */
  public ResultadoDaConfirmacao confirmar(
      @Nullable String token, String ip, Optional<String> tokenDaSessaoAnterior) {
    limiteDeTaxa.consumir(LIMITE_POR_IP, ip);
    if (token == null || token.isEmpty() || token.length() > TAMANHO_MAXIMO_DO_TOKEN) {
      throw linkInvalido();
    }
    ResultadoDaConfirmacao resultado =
        transacao.execute(status -> efetuar(token, tokenDaSessaoAnterior));
    if (resultado == null) {
      throw new IllegalStateException("A transação da confirmação não devolveu resultado");
    }
    return resultado;
  }

  private ResultadoDaConfirmacao efetuar(String token, Optional<String> tokenDaSessaoAnterior) {
    Email email =
        links
            .consumir(Resumos.sha256(token), relogio.instant())
            .orElseThrow(ConfirmacaoDeEntrada::linkInvalido);
    if (bloqueios.bloqueado(resumoDeEmail.de(email))) {
      throw linkInvalido();
    }
    Optional<Conta> existente = contas.porEmail(email);
    ContaId conta = existente.map(Conta::id).orElseGet(() -> contas.criarPendente(email));
    EstadoConta estado = existente.map(Conta::estado).orElse(EstadoConta.PENDENTE);
    if (!estado.disponivel()) {
      throw new ErroDeNegocio(
          CodigoSessao.CONTA_INDISPONIVEL, CodigoSessao.CONTA_INDISPONIVEL.mensagem());
    }
    tokenDaSessaoAnterior.ifPresent(sessoes::encerrar);
    String tokenDaSessao = sessoes.abrir(conta);
    LOG.info("Entrada confirmada para a conta {}", conta);
    return new ResultadoDaConfirmacao(tokenDaSessao, estado.cadastroConcluido());
  }

  private static ErroDeNegocio linkInvalido() {
    return new ErroDeNegocio(
        CodigoEntrada.LINK_INVALIDO, "Este link expirou ou já foi usado. Peça um novo.");
  }
}
