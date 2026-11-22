package bipotheking.net.duora.game;

import java.util.Objects;
import java.util.Optional;

/**
 * Fases do terraço enquanto a sala está aberta (padrão State). O encerramento é tratado pela sala.
 * Cada variante é um valor: o que for próprio de uma fase mora nela, e as {@link Regras} só criam
 * uma nova instância ao entrar na fase, preservando a atual nas jogadas dentro dela.
 */
public sealed interface Fase {

  /** Nome da fase no contrato da sala. */
  String nome();

  /** Só um assento entrou; espera o par pelo convite. */
  record AguardandoPar() implements Fase {
    @Override
    public String nome() {
      return "AGUARDANDO_PAR";
    }
  }

  /** Os dois montam a composição e confirmam uma versão. */
  record Montando() implements Fase {
    @Override
    public String nome() {
      return "MONTANDO";
    }
  }

  /**
   * As duas confirmações na mesma versão revelaram o céu.
   *
   * @param pedidoDeAjuste quem pediu para voltar à montagem e espera o aceite do outro
   */
  record Revelado(Optional<Assento> pedidoDeAjuste) implements Fase {

    public Revelado {
      Objects.requireNonNull(pedidoDeAjuste);
    }

    /** Revelação sem pedido de ajuste. */
    public Revelado() {
      this(Optional.empty());
    }

    @Override
    public String nome() {
      return "REVELADO";
    }
  }
}
