package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusa da denúncia pública. A mensagem do contrato depende do campo recusado. */
public enum CodigoDenuncia implements CodigoDeErro {
  DENUNCIA_PUBLICA_INVALIDA("Denúncia pública inválida");

  /** Os campos da denúncia, cada um com a mensagem do contrato. */
  public enum Campo {
    MOTIVO("Escolha o que aconteceu."),
    DESCRICAO("Conte o que aconteceu em 10 a 2000 caracteres."),
    REFERENCIA("Use até 200 caracteres no apelido ou link."),
    EMAIL_CONTATO("Confira o e-mail para resposta.");

    private final String mensagem;

    Campo(String mensagem) {
      this.mensagem = mensagem;
    }

    /** O texto para quem usa o app. */
    public String mensagem() {
      return mensagem;
    }
  }

  private final String titulo;

  CodigoDenuncia(String titulo) {
    this.titulo = titulo;
  }

  @Override
  public TipoDeErro tipo() {
    return TipoDeErro.REGRA_DE_NEGOCIO;
  }

  @Override
  public String titulo() {
    return titulo;
  }

  /** A recusa do campo, com a mensagem do contrato. */
  public ErroDeNegocio erro(Campo campo) {
    return new ErroDeNegocio(this, campo.mensagem());
  }
}
