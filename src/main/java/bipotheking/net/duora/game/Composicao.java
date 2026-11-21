package bipotheking.net.duora.game;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Onde cada peça está. Peça ausente do mapa está no inventário. Imutável. */
public record Composicao(Map<Peca, Lugar> lugares) {

  private static final int PECAS_POR_PESSOA_COMPLETA = 2;

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
        .filter(entrada -> entrada.getValue() == lugar)
        .map(Map.Entry::getKey)
        .findFirst();
  }

  public boolean colocada(Peca peca) {
    return lugares.containsKey(peca);
  }

  public int colocadas(Assento dono) {
    return (int) lugares.keySet().stream().filter(peca -> peca.dono() == dono).count();
  }

  public Optional<Peca> secundariaDe(Assento dono) {
    return Peca.secundariasDe(dono).stream().filter(this::colocada).findFirst();
  }

  /** Composição completa: cada pessoa com a obrigatória e uma secundária no terraço. */
  public boolean completa() {
    return faltam() == 0;
  }

  /** Quantas peças faltam para a composição ficar completa (0 a 4). */
  public int faltam() {
    int faltam = 0;
    for (Assento assento : Assento.values()) {
      faltam += PECAS_POR_PESSOA_COMPLETA - colocadas(assento);
    }
    return faltam;
  }

  public Composicao com(Peca peca, Lugar lugar) {
    Map<Peca, Lugar> novos = new EnumMap<>(Peca.class);
    novos.putAll(lugares);
    novos.put(peca, lugar);
    return new Composicao(novos);
  }

  public Composicao sem(Peca peca) {
    if (!colocada(peca)) {
      return this;
    }
    Map<Peca, Lugar> novos = new EnumMap<>(Peca.class);
    novos.putAll(lugares);
    novos.remove(peca);
    return new Composicao(novos);
  }
}
