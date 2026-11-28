package bipotheking.net.duora.perfil;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * O que o pareamento precisa saber da conta: intenções, ajustes de busca e, com namoro, o gênero
 * próprio e os buscados. Só para uso interno do servidor: nunca serializada nem logada (o {@code
 * toString} não carrega gênero nem interesses). Sem dados de namoro, {@code generoProprio} vem
 * vazio e {@code generosBuscados}, vazio.
 */
public record Preferencias(
    Set<Intencao> intencao,
    FaixaEtaria faixaEtaria,
    Raio raio,
    boolean raioObrigatorio,
    Optional<Genero> generoProprio,
    Set<Genero> generosBuscados) {

  public Preferencias {
    Objects.requireNonNull(intencao, "intencao");
    Objects.requireNonNull(faixaEtaria, "faixaEtaria");
    Objects.requireNonNull(raio, "raio");
    Objects.requireNonNull(generoProprio, "generoProprio");
    Objects.requireNonNull(generosBuscados, "generosBuscados");
    intencao = Collections.unmodifiableSet(copia(intencao, Intencao.class));
    generosBuscados = Collections.unmodifiableSet(copia(generosBuscados, Genero.class));
    raioObrigatorio = raioObrigatorio && raio != Raio.SEM_LIMITE;
  }

  private static <E extends Enum<E>> EnumSet<E> copia(Set<E> origem, Class<E> tipo) {
    EnumSet<E> copia = EnumSet.noneOf(tipo);
    copia.addAll(origem);
    return copia;
  }

  @Override
  public String toString() {
    return "Preferencias[intencao=%s, faixaEtaria=%s, raio=%s, raioObrigatorio=%s, ***]"
        .formatted(intencao, faixaEtaria, raio, raioObrigatorio);
  }
}
