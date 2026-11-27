package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas de quem tem sessão, mas não pode seguir. Cada código traz a mensagem do contrato. */
public enum CodigoSessao implements CodigoDeErro {
  CONTA_INDISPONIVEL("Conta indisponível", "Esta conta não está disponível."),
  CADASTRO_INCOMPLETO("Cadastro incompleto", "Conclua o cadastro para continuar.");

  private final String titulo;
  private final String mensagem;

  CodigoSessao(String titulo, String mensagem) {
    this.titulo = titulo;
    this.mensagem = mensagem;
  }

  @Override
  public TipoDeErro tipo() {
    return TipoDeErro.PROIBIDO;
  }

  @Override
  public String titulo() {
    return titulo;
  }

  /** O texto para quem usa o app, igual em toda rota que recusa com este código. */
  public String mensagem() {
    return mensagem;
  }
}
