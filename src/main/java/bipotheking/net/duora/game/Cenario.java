package bipotheking.net.duora.game;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Um cenário como dados: lugares, peças, parâmetros das regras e o {@link Desfecho} da revelação. O
 * construtor recusa dados inconsistentes com {@link IllegalArgumentException} citando o valor.
 *
 * @param id permanente, casa {@code ^[a-z0-9-]+$}; nunca renomeado nem removido
 * @param limiteAntesDoParceiro quantas peças cada pessoa coloca antes de o parceiro colocar alguma
 * @param duracaoSinal quanto dura um “que tal mover este?”
 * @param pecasPorPessoaCompleta quantas peças de cada pessoa completam a composição
 */
public record Cenario(
    String id,
    String nome,
    List<Lugar> lugares,
    List<Peca> pecas,
    int limiteAntesDoParceiro,
    Duration duracaoSinal,
    int pecasPorPessoaCompleta,
    Desfecho desfecho) {

  private static final Pattern ID = Pattern.compile("^[a-z0-9-]+$");

  public Cenario {
    Objects.requireNonNull(id);
    Objects.requireNonNull(nome);
    Objects.requireNonNull(duracaoSinal);
    Objects.requireNonNull(desfecho);
    lugares = List.copyOf(lugares);
    pecas = List.copyOf(pecas);
    if (!ID.matcher(id).matches()) {
      throw new IllegalArgumentException("id de cenário fora do padrão: " + id);
    }
    if (nome.isBlank()) {
      throw new IllegalArgumentException("nome vazio no cenário " + id);
    }
    exigirIdsUnicos(lugares.stream().map(Lugar::id).toList(), "lugar");
    exigirIdsUnicos(pecas.stream().map(Peca::id).toList(), "peça");
    for (Peca peca : pecas) {
      if (lugares.stream().noneMatch(peca::aceita)) {
        throw new IllegalArgumentException(
            "zona sem lugar: " + peca.zona().id() + " (peça " + peca.id() + ")");
      }
    }
    if (limiteAntesDoParceiro < 1) {
      throw new IllegalArgumentException(
          "limiteAntesDoParceiro menor que 1: " + limiteAntesDoParceiro);
    }
    if (duracaoSinal.isNegative() || duracaoSinal.isZero()) {
      throw new IllegalArgumentException("duracaoSinal não positiva: " + duracaoSinal);
    }
    for (Assento assento : Assento.values()) {
      long doAssento = pecas.stream().filter(peca -> peca.dono() == assento).count();
      if (pecasPorPessoaCompleta < 1 || pecasPorPessoaCompleta > doAssento) {
        throw new IllegalArgumentException(
            "pecasPorPessoaCompleta "
                + pecasPorPessoaCompleta
                + " fora de 1.."
                + doAssento
                + " (assento "
                + assento
                + ")");
      }
    }
  }

  public Optional<Peca> peca(String id) {
    return pecas.stream().filter(peca -> peca.id().equals(id)).findFirst();
  }

  public Optional<Lugar> lugar(String id) {
    return lugares.stream().filter(lugar -> lugar.id().equals(id)).findFirst();
  }

  /** Lugares da zona, na ordem de {@link #lugares()}. */
  public List<Lugar> da(Zona zona) {
    return lugares.stream().filter(lugar -> lugar.zona().equals(zona)).toList();
  }

  /** Peças do dono, na ordem de {@link #pecas()}. */
  public List<Peca> pecasDe(Assento dono) {
    return pecas.stream().filter(peca -> peca.dono() == dono).toList();
  }

  /** Peças não obrigatórias do dono, na ordem de {@link #pecas()}. */
  public List<Peca> secundariasDe(Assento dono) {
    return pecasDe(dono).stream().filter(peca -> !peca.obrigatoria()).toList();
  }

  /** Quantas peças faltam para a composição ficar completa. */
  public int faltam(Composicao composicao) {
    int faltam = 0;
    for (Assento assento : Assento.values()) {
      faltam += Math.max(0, pecasPorPessoaCompleta - composicao.colocadas(assento));
    }
    return faltam;
  }

  /** Composição completa: cada pessoa com {@link #pecasPorPessoaCompleta()} peças colocadas. */
  public boolean completa(Composicao composicao) {
    return faltam(composicao) == 0;
  }

  private static void exigirIdsUnicos(List<String> ids, String tipo) {
    Set<String> vistos = new HashSet<>();
    for (String id : ids) {
      if (!vistos.add(id)) {
        throw new IllegalArgumentException("id de " + tipo + " repetido: " + id);
      }
    }
  }
}
