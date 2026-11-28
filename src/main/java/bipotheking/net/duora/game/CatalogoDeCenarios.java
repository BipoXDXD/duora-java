package bipotheking.net.duora.game;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Os cenários jogáveis, na ordem de registro, e os sorteáveis para um encontro, na ordem dada.
 * Imutável; lista vazia, id repetido ou sorteável desconhecido recusados com {@link
 * IllegalArgumentException}, para a aplicação não subir com um catálogo inconsistente.
 */
public final class CatalogoDeCenarios {

  private final List<Cenario> cenarios;
  private final List<Cenario> sorteaveis;

  /** Catálogo com todos os cenários sorteáveis, na ordem de registro. */
  public CatalogoDeCenarios(List<Cenario> cenarios) {
    this.cenarios = validar(cenarios);
    this.sorteaveis = this.cenarios;
  }

  /** Catálogo em que só os ids dados são sorteáveis, nessa ordem. */
  public CatalogoDeCenarios(List<Cenario> cenarios, List<String> sorteaveis) {
    this.cenarios = validar(cenarios);
    if (sorteaveis.isEmpty()) {
      throw new IllegalArgumentException("nenhum cenário sorteável");
    }
    Set<String> vistos = new HashSet<>();
    List<Cenario> escolhidos = new ArrayList<>();
    for (String id : sorteaveis) {
      if (!vistos.add(id)) {
        throw new IllegalArgumentException("cenário sorteável repetido: " + id);
      }
      escolhidos.add(
          porId(id)
              .orElseThrow(
                  () -> new IllegalArgumentException("cenário sorteável desconhecido: " + id)));
    }
    this.sorteaveis = List.copyOf(escolhidos);
  }

  private static List<Cenario> validar(List<Cenario> cenarios) {
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
    return copia;
  }

  /** Busca pelo id (nunca por {@code equals} de {@link Cenario}, que compara o desfecho). */
  public Optional<Cenario> porId(String id) {
    return cenarios.stream().filter(cenario -> cenario.id().equals(id)).findFirst();
  }

  public List<Cenario> todos() {
    return cenarios;
  }

  /** Os cenários que um encontro pode sortear, na ordem configurada. */
  public List<Cenario> sorteaveis() {
    return sorteaveis;
  }
}
