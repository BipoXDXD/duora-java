package bipotheking.net.duora.game;

import java.time.Duration;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A abertura que um cenário declara antes da montagem. O construtor recusa dados inconsistentes com
 * {@link IllegalArgumentException} citando o valor.
 *
 * @param tipo nome-base da abertura, casa {@code ^[a-z0-9-]+$}
 * @param janela quanto tempo a abertura espera pelo toque das duas pessoas; positiva
 * @param maximo teto da abertura; {@link Duration#ZERO} conclui apagada já na entrada
 */
public record DefinicaoDeAbertura(String tipo, Duration janela, Duration maximo) {

  private static final Pattern TIPO = Pattern.compile("^[a-z0-9-]+$");

  public DefinicaoDeAbertura {
    Objects.requireNonNull(tipo);
    Objects.requireNonNull(janela);
    Objects.requireNonNull(maximo);
    if (!TIPO.matcher(tipo).matches()) {
      throw new IllegalArgumentException("tipo de abertura fora do padrão: " + tipo);
    }
    if (!janela.isPositive()) {
      throw new IllegalArgumentException("janela de abertura não positiva: " + janela);
    }
    if (maximo.isNegative()) {
      throw new IllegalArgumentException("maximo de abertura negativo: " + maximo);
    }
  }

  /** A mesma abertura com outro {@link #maximo()}. */
  DefinicaoDeAbertura comMaximo(Duration maximo) {
    return new DefinicaoDeAbertura(tipo, janela, maximo);
  }
}
