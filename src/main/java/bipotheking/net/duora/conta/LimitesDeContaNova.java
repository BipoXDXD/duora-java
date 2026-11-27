package bipotheking.net.duora.conta;

import bipotheking.net.duora.conta.dominio.RegraDeContaNova;
import bipotheking.net.duora.conta.dominio.RepositorioDeUsoDeContaNova;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Limites diários da conta nova: nos primeiros 7 dias depois da ativação, cada mensagem, imagem ou
 * convite consome um do limite do dia de São Paulo. As Etapas 4 e 5 chamam {@link #consumir} na
 * transação do efeito: se o efeito falha, o consumo volta junto; se o limite acabou, o efeito não
 * acontece.
 */
@Service
public class LimitesDeContaNova {

  private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

  private final ConsultaDeContas consultas;
  private final RepositorioDeUsoDeContaNova uso;
  private final LimitesDeContaNovaConfig config;
  private final Clock relogio;

  LimitesDeContaNova(
      ConsultaDeContas consultas,
      RepositorioDeUsoDeContaNova uso,
      LimitesDeContaNovaConfig config,
      Clock relogio) {
    this.consultas = consultas;
    this.uso = uso;
    this.config = config;
    this.relogio = relogio;
  }

  /**
   * Consome um do limite do dia, se a conta ainda é nova. Conta sem {@code ativada_em} conta como
   * nova: na dúvida, o limite vale. O uso do dia fica travado até o commit do chamador, então
   * consumos da mesma conta e tipo esperam um pelo outro.
   *
   * <p>Para quem chama (Etapas 4 e 5):
   *
   * <ul>
   *   <li>não capture o {@link LimiteDeContaNovaExcedido} dentro da transação: ao atravessar este
   *       método, a exceção já marcou a transação como rollback-only, e o commit falharia. Deixe a
   *       exceção sair da transação e trate-a fora dela;
   *   <li>quem consome mais de um tipo na mesma transação consome na ordem do enum {@link
   *       TipoDeLimiteNovo} ({@code MENSAGEM}, {@code IMAGEM}, {@code CONVITE}): duas transações
   *       que travassem os tipos em ordens opostas entrariam em deadlock.
   * </ul>
   *
   * @throws LimiteDeContaNovaExcedido se o limite do dia acabou, com espera até a meia-noite de São
   *     Paulo
   * @throws IllegalArgumentException se a conta não existe
   * @throws org.springframework.transaction.IllegalTransactionStateException fora de transação
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void consumir(ContaId conta, TipoDeLimiteNovo tipo) {
    Instant agora = relogio.instant();
    Instant ativadaEm =
        consultas
            .porId(conta)
            .orElseThrow(() -> new IllegalArgumentException("Conta inexistente: " + conta))
            .ativadaEm();
    if (ativadaEm != null && !RegraDeContaNova.emPeriodoDeContaNova(ativadaEm, agora)) {
      return;
    }
    int limite =
        RegraDeContaNova.limite(
            tipo, config.mensagensPorDia(), config.imagensPorDia(), config.convitesPorDia());
    LocalDate hoje = LocalDate.ofInstant(agora, FUSO);
    if (uso.consumir(conta, hoje, tipo, limite).isEmpty()) {
      Instant meiaNoite = hoje.plusDays(1).atStartOfDay(FUSO).toInstant();
      throw new LimiteDeContaNovaExcedido(Duration.between(agora, meiaNoite));
    }
  }
}
