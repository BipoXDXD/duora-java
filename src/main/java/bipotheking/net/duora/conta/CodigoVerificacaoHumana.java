package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Códigos de erro da verificação humana. */
public enum CodigoVerificacaoHumana implements CodigoDeErro {
  VERIFICACAO_HUMANA_FALHOU(TipoDeErro.REGRA_DE_NEGOCIO, "Verificação humana falhou");

  private final TipoDeErro tipo;
  private final String titulo;

  CodigoVerificacaoHumana(TipoDeErro tipo, String titulo) {
    this.tipo = tipo;
    this.titulo = titulo;
  }

  @Override
  public TipoDeErro tipo() {
    return tipo;
  }

  @Override
  public String titulo() {
    return titulo;
  }
}
