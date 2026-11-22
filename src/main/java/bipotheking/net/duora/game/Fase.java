package bipotheking.net.duora.game;

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

  /** As duas confirmações na mesma versão revelaram o céu. */
  record Revelado() implements Fase {
    @Override
    public String nome() {
      return "REVELADO";
    }
  }
}
