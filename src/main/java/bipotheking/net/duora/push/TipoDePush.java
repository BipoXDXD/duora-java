package bipotheking.net.duora.push;

/**
 * O que um push avisa. O payload leva só o {@link #valor()} (H4): título e texto ficam fixos no
 * service worker, e nada pessoal aparece na tela bloqueada.
 */
public enum TipoDePush {
  COMPATIVEL("compativel");

  private final String valor;

  TipoDePush(String valor) {
    this.valor = valor;
  }

  /** O valor do campo {@code tipo} no payload, como {@code compativel}. */
  public String valor() {
    return valor;
  }

  /** O cabeçalho {@code Topic}: um aviso novo do mesmo tipo substitui o que ainda não chegou. */
  public String tag() {
    return "duora-" + valor;
  }
}
