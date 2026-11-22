package bipotheking.net.duora.game;

import java.util.Objects;
import java.util.Optional;

/**
 * Fases do terraço enquanto a sala está aberta, como tipo selado; o encerramento é tratado pela
 * sala. Cada variante é um valor e carrega o que é próprio dela (a abertura na montagem, o pedido
 * de ajuste na revelação). As {@link Regras} decidem por variante e criam uma nova instância só
 * quando a fase muda ou muda o que ela carrega; as demais jogadas preservam a instância atual.
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

  /**
   * Os dois montam a composição e confirmam uma versão.
   *
   * @param abertura a abertura do encontro, ativa ou já concluída; vazia quando o cenário não
   *     declara abertura e na montagem depois de um ajuste (a abertura acontece uma vez por
   *     encontro)
   */
  record Montando(Optional<Abertura> abertura) implements Fase {

    public Montando {
      Objects.requireNonNull(abertura);
    }

    /** Montagem sem abertura. */
    public Montando() {
      this(Optional.empty());
    }

    /** Se a abertura ainda espera os toques; enquanto isso, a montagem fica parada. */
    public boolean aberturaAtiva() {
      return abertura.filter(Abertura::ativa).isPresent();
    }

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
