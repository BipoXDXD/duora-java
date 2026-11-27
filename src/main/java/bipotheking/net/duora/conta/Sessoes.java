package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.RepositorioDeSessoes;
import bipotheking.net.duora.conta.dominio.RepositorioDeSessoes.SessaoGravada;
import bipotheking.net.duora.conta.dominio.Token;
import bipotheking.net.duora.nucleo.GeradorDeIds;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Service;

/**
 * Sessões de login. Cada uma vale 30 dias desde a última renovação; a renovação deslizante grava no
 * máximo uma vez a cada 24 h. O token só existe no cookie: o banco guarda o resumo.
 */
@Service
public class Sessoes {

  private static final Duration DURACAO = Duration.ofDays(30);
  private static final Duration INTERVALO_DE_RENOVACAO = Duration.ofHours(24);

  private final RepositorioDeSessoes repositorio;
  private final Clock relogio;
  private final GeradorDeIds geradorDeIds;
  private final RandomGenerator aleatorio;

  Sessoes(
      RepositorioDeSessoes repositorio,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      RandomGenerator aleatorio) {
    this.repositorio = repositorio;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.aleatorio = aleatorio;
  }

  /** Abre uma sessão para a conta e devolve o token, que vai só para o cookie. */
  public String abrir(ContaId conta) {
    Token token = Token.novo(aleatorio);
    Instant agora = relogio.instant();
    repositorio.gravar(geradorDeIds.novo(), conta, token.resumo(), agora, agora.plus(DURACAO));
    return token.valor();
  }

  /**
   * A sessão do token, se existir e não tiver vencido ({@code expira_em > agora}). Renova quando a
   * última renovação passou de 24 h.
   */
  public Optional<SessaoAtiva> validar(String token) {
    Instant agora = relogio.instant();
    return repositorio
        .porResumo(Token.de(token).resumo())
        .filter(sessao -> sessao.expiraEm().isAfter(agora))
        .map(sessao -> new SessaoAtiva(sessao.conta(), sessao.estado(), renovar(sessao, agora)));
  }

  /** Apaga a sessão do token (logout). Token desconhecido não faz nada. */
  public void encerrar(String token) {
    repositorio.apagar(Token.de(token).resumo());
  }

  /** Apaga todas as sessões da conta. */
  public void revogarTodas(ContaId conta) {
    repositorio.apagarDaConta(conta);
  }

  /** Só tenta gravar se a renovação venceu; o UPDATE condicional decide entre concorrentes. */
  private boolean renovar(SessaoGravada sessao, Instant agora) {
    Instant limite = agora.minus(INTERVALO_DE_RENOVACAO);
    return sessao.renovadaEm().isBefore(limite)
        && repositorio.renovar(sessao.id(), agora, agora.plus(DURACAO), limite);
  }
}
