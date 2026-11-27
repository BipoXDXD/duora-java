package bipotheking.net.duora.conta.dominio;

import bipotheking.net.duora.conta.TipoDeLimiteNovo;
import java.time.Duration;
import java.time.Instant;

/** Quando a conta é nova e quanto ela pode consumir por dia. */
public final class RegraDeContaNova {

  private static final Duration PERIODO = Duration.ofDays(7);

  private RegraDeContaNova() {}

  /**
   * Menos de 7 dias desde a ativação. Um {@code agora} anterior à ativação (relógio fora de ordem)
   * conta como dentro: na dúvida, o limite vale.
   */
  public static boolean emPeriodoDeContaNova(Instant ativadaEm, Instant agora) {
    return Duration.between(ativadaEm, agora).compareTo(PERIODO) < 0;
  }

  /** O limite diário do tipo, entre os três configurados. */
  public static int limite(TipoDeLimiteNovo tipo, int mensagens, int imagens, int convites) {
    return switch (tipo) {
      case MENSAGEM -> mensagens;
      case IMAGEM -> imagens;
      case CONVITE -> convites;
    };
  }
}
