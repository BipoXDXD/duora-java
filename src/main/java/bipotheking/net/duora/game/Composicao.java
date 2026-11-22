package bipotheking.net.duora.game;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Onde cada peça está. Peça ausente do mapa está no inventário. Imutável. */
public record Composicao(Map<Peca, Lugar> lugares) {

  public Composicao {
    lugares = Map.copyOf(lugares);
  }

  public static Composicao vazia() {
    return new Composicao(Map.of());
  }

  public Optional<Lugar> lugarDe(Peca peca) {
    return Optional.ofNullable(lugares.get(peca));
  }

  public Optional<Peca> ocupante(Lugar lugar) {
    return lugares.entrySet().stream()
        .filter(entrada -> entrada.getValue().equals(lugar))
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public boolean colocada(Peca peca) {
    return lugares.containsKey(peca);
  }

  public int colocadas(Assento dono) {
    return (int) lugares.keySet().stream().filter(peca -> peca.dono() == dono).count();
  }

  public Composicao com(Peca peca, Lugar lugar) {
    Map<Peca, Lugar> novos = new HashMap<>();
    novos.putAll(lugares);
    novos.put(peca, lugar);
    return new Composicao(novos);
  }

  public Composicao sem(Peca peca) {
    if (!colocada(peca)) {
      return this;
    }
    Map<Peca, Lugar> novos = new HashMap<>();
    novos.putAll(lugares);
    novos.remove(peca);
    return new Composicao(novos);
  }
}
