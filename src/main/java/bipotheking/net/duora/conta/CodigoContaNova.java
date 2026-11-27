package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusa da conta nova que chegou ao limite do dia. */
public enum CodigoContaNova implements CodigoDeErro {
  LIMITE_DE_CONTA_NOVA(
      "Limite de conta nova",
      "Contas novas têm um limite por dia nos primeiros 7 dias. Tente de novo amanhã.");

  private final String titulo;
  private final String mensagem;

  CodigoContaNova(String titulo, String mensagem) {
    this.titulo = titulo;
    this.mensagem = mensagem;
  }

  @Override
  public TipoDeErro tipo() {
    return TipoDeErro.LIMITE;
  }

  @Override
  public String titulo() {
    return titulo;
  }

  /** O texto para quem usa o app. */
  public String mensagem() {
    return mensagem;
  }
}
