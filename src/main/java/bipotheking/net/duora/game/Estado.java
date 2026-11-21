package bipotheking.net.duora.game;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Estado do terraço. Imutável: toda mudança aceita pelas {@link Regras} produz um novo estado.
 *
 * @param versao incrementa a cada mudança de composição e em “ajustar”; confirmações valem para uma
 *     versão
 */
public record Estado(
    Fase fase, long versao, Composicao composicao, Set<Assento> prontos, Optional<Sinal> sinal) {

  public Estado {
    Objects.requireNonNull(fase);
    Objects.requireNonNull(composicao);
    prontos = Set.copyOf(prontos);
    Objects.requireNonNull(sinal);
  }

  public static Estado inicial() {
    return new Estado(Fase.AGUARDANDO_PAR, 0, Composicao.vazia(), Set.of(), Optional.empty());
  }

  public boolean pronto(Assento assento) {
    return prontos.contains(assento);
  }

  public Optional<Sinal> sinalAtivoEm(Instant agora) {
    return sinal.filter(s -> s.ativoEm(agora));
  }
}
