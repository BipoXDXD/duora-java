package bipotheking.net.duora.game;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Os cenários jogáveis, na ordem de registro. Imutável; lista vazia ou id repetido recusados com
 * {@link IllegalArgumentException}, para a aplicação não subir com um catálogo inconsistente.
 */
public final class CatalogoDeCenarios {

  private final List<Cenario> cenarios;

  public CatalogoDeCenarios(List<Cenario> cenarios) {
    List<Cenario> copia = List.copyOf(cenarios);
    if (copia.isEmpty()) {
      throw new IllegalArgumentException("catálogo de cenários vazio");
    }
    Set<String> ids = new HashSet<>();
    for (Cenario cenario : copia) {
      if (!ids.add(cenario.id())) {
        throw new IllegalArgumentException("id de cenário repetido: " + cenario.id());
      }
    }
    this.cenarios = copia;
  }

  /** Busca pelo id (nunca por {@code equals} de {@link Cenario}, que compara o desfecho). */
  public Optional<Cenario> porId(String id) {
    return cenarios.stream().filter(cenario -> cenario.id().equals(id)).findFirst();
  }

  public List<Cenario> todos() {
    return cenarios;
  }
}
