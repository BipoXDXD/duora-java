package bipotheking.net.duora.conta.dominio;

import java.time.LocalDate;
import java.time.Period;

/** Maioridade pela data de nascimento declarada. */
final class Idade {

  private static final int MAIORIDADE = 18;

  private Idade() {}

  /**
   * Dezoito anos completos em {@code hoje}, contados por {@link Period#between}: quem nasceu em 29
   * de fevereiro completa anos em 1º de março nos anos não bissextos.
   */
  static boolean maiorDeIdade(LocalDate nascimento, LocalDate hoje) {
    return Period.between(nascimento, hoje).getYears() >= MAIORIDADE;
  }
}
