package bipotheking.net.duora.perfil.dominio;

import bipotheking.net.duora.perfil.CodigoPerfil;
import bipotheking.net.duora.perfil.Intencao;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Ao menos uma {@link Intencao}, sem repetição, na ordem do enum. O conjunto é imutável. */
public record Intencoes(Set<Intencao> valores) {

  public static final Intencoes SO_AMIZADE = new Intencoes(EnumSet.of(Intencao.AMIZADE));

  public Intencoes {
    Objects.requireNonNull(valores, "valores");
    if (valores.isEmpty()) {
      throw new IllegalArgumentException("Intenções vazias");
    }
    valores = Collections.unmodifiableSet(EnumSet.copyOf(valores));
  }

  /** Ausente, vazia ou com item nulo vira {@code INTENCAO_INVALIDA}; repetidas contam uma vez. */
  public static Intencoes de(@Nullable Collection<Intencao> brutas) {
    if (brutas == null || brutas.isEmpty() || brutas.stream().anyMatch(Objects::isNull)) {
      throw CodigoPerfil.INTENCAO_INVALIDA.erro();
    }
    return new Intencoes(EnumSet.copyOf(brutas));
  }

  public boolean querAmizade() {
    return valores.contains(Intencao.AMIZADE);
  }

  public boolean querNamoro() {
    return valores.contains(Intencao.NAMORO);
  }
}
