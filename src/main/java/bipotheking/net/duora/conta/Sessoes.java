package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.RepositorioDeSessoes;
import bipotheking.net.duora.conta.dominio.RepositorioDeSessoes.SessaoGravada;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import bipotheking.net.duora.nucleo.Origem;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sessões de login. Cada uma vale 30 dias desde a última renovação; a renovação deslizante grava no
 * máximo uma vez a cada 24 h. O token só existe no cookie: o banco guarda o resumo. O login e cada
 * renovação gravada deixam um registro de acesso na mesma transação.
 */
@Service
public class Sessoes {

  private static final Duration DURACAO = Duration.ofDays(30);
  private static final Duration INTERVALO_DE_RENOVACAO = Duration.ofHours(24);

  private final RepositorioDeSessoes repositorio;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;
  private final RandomGenerator aleatorio;
  private final RegistroDeAcessos acessos;
  private final TransactionTemplate transacao;

  Sessoes(
      RepositorioDeSessoes repositorio,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      RandomGenerator aleatorio,
      RegistroDeAcessos acessos,
      TransactionTemplate transacao) {
    this.repositorio = repositorio;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.aleatorio = aleatorio;
    this.acessos = acessos;
    this.transacao = transacao;
  }

  /**
   * Abre uma sessão para a conta, registra o {@code LOGIN} vindo de {@code origem} e devolve o
   * token, que vai só para o cookie.
   */
  @Transactional
  public String abrir(ContaId conta, Origem origem) {
    Token token = Token.novo(aleatorio);
    Instant agora = relogio.instant();
    repositorio.gravar(geradorDeIds.novo(), conta, token.resumo(), agora, agora.plus(DURACAO));
    acessos.registrar(Optional.of(conta), origem, EventoDeAcesso.LOGIN);
    return token.valor();
  }

  /**
   * A sessão do token, se existir e não tiver vencido ({@code expira_em > agora}). Renova quando a
   * última renovação passou de 24 h; só a renovação gravada registra a {@code RENOVACAO} vinda de
   * {@code origem}. Só a renovação abre transação: a leitura comum não.
   */
  public Optional<SessaoAtiva> validar(String token, Origem origem) {
    Instant agora = relogio.instant();
    return repositorio
        .porResumo(Token.de(token).resumo())
        .filter(sessao -> sessao.expiraEm().isAfter(agora))
        .map(sessao ->
            new SessaoAtiva(sessao.conta(), sessao.estado(), renovar(sessao, origem, agora)));
  }

  /** Apaga a sessão do token (logout). Token desconhecido não faz nada. */
  public void encerrar(String token) {
    repositorio.apagar(Token.de(token).resumo());
  }

  /** Apaga todas as sessões da conta. */
  public void revogarTodas(ContaId conta) {
    repositorio.apagarDaConta(conta);
  }

  /** Só tenta gravar se a renovação venceu, no máximo uma vez a cada 24 h por sessão. */
  private boolean renovar(SessaoGravada sessao, Origem origem, Instant agora) {
    Instant limite = agora.minus(INTERVALO_DE_RENOVACAO);
    return sessao.renovadaEm().isBefore(limite)
        && Boolean.TRUE.equals(
            transacao.execute(status -> gravarRenovacao(sessao, origem, agora, limite)));
  }

  /**
   * O UPDATE condicional e o registro numa transação: o lock da linha vale até o commit, então
   * entre concorrentes só o vencedor do UPDATE registra o acesso.
   */
  private boolean gravarRenovacao(
      SessaoGravada sessao, Origem origem, Instant agora, Instant limite) {
    boolean renovada = repositorio.renovar(sessao.id(), agora, agora.plus(DURACAO), limite);
    if (renovada) {
      acessos.registrar(Optional.of(sessao.conta()), origem, EventoDeAcesso.RENOVACAO);
    }
    return renovada;
  }
}
