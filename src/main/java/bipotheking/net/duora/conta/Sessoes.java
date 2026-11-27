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

  Sessoes(
      RepositorioDeSessoes repositorio,
      Clock relogio,
      GeradorDeIds geradorDeIds,
      RandomGenerator aleatorio,
      RegistroDeAcessos acessos) {
    this.repositorio = repositorio;
    this.relogio = relogio;
    this.geradorDeIds = geradorDeIds;
    this.aleatorio = aleatorio;
    this.acessos = acessos;
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
   * {@code origem}.
   */
  @Transactional
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

  /**
   * Só tenta gravar se a renovação venceu; o UPDATE condicional decide entre concorrentes, e só o
   * vencedor registra o acesso.
   */
  private boolean renovar(SessaoGravada sessao, Origem origem, Instant agora) {
    Instant limite = agora.minus(INTERVALO_DE_RENOVACAO);
    boolean renovada =
        sessao.renovadaEm().isBefore(limite)
            && repositorio.renovar(sessao.id(), agora, agora.plus(DURACAO), limite);
    if (renovada) {
      acessos.registrar(Optional.of(sessao.conta()), origem, EventoDeAcesso.RENOVACAO);
    }
    return renovada;
  }
}
