package bipotheking.net.duora.push;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas do Web Push. A mensagem nunca cita o endpoint recebido. */
public enum CodigoPush implements CodigoDeErro {
  INSCRICAO_INVALIDA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Inscrição inválida",
      "Não conseguimos ativar os avisos neste navegador."),
  PUSH_INDISPONIVEL(
      TipoDeErro.NAO_ENCONTRADO, "Push indisponível", "Os avisos não estão disponíveis agora.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoPush(TipoDeErro tipo, String titulo, String mensagem) {
    this.tipo = tipo;
    this.titulo = titulo;
    this.mensagem = mensagem;
  }

  @Override
  public TipoDeErro tipo() {
    return tipo;
  }

  @Override
  public String titulo() {
    return titulo;
  }

  /** O texto para quem usa o app. */
  public String mensagem() {
    return mensagem;
  }

  /** A recusa com a mensagem do contrato. */
  public ErroDeNegocio erro() {
    return new ErroDeNegocio(this, mensagem);
  }
}
