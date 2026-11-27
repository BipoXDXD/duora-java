package bipotheking.net.duora.web;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Códigos de erro da plataforma web, comuns a todos os módulos. */
public enum CodigoWeb implements CodigoDeErro {
  ORIGEM_RECUSADA(TipoDeErro.PROIBIDO, "Origem recusada"),
  HOST_RECUSADO(TipoDeErro.ENDERECO_RECUSADO, "Host recusado"),
  CORPO_INVALIDO(TipoDeErro.ENTRADA_INVALIDA, "Corpo inválido"),
  REQUISICAO_INVALIDA(TipoDeErro.ENTRADA_INVALIDA, "Requisição inválida"),
  RECURSO_INEXISTENTE(TipoDeErro.NAO_ENCONTRADO, "Recurso inexistente"),
  METODO_NAO_PERMITIDO(TipoDeErro.METODO_NAO_PERMITIDO, "Método não permitido"),
  FORMATO_NAO_SUPORTADO(TipoDeErro.FORMATO_NAO_SUPORTADO, "Formato não suportado"),
  NAO_AUTENTICADO(TipoDeErro.NAO_AUTENTICADO, "Não autenticado"),
  LIMITE_DE_TAXA(TipoDeErro.LIMITE, "Limite de taxa"),
  CHAVE_DE_IDEMPOTENCIA_INVALIDA(TipoDeErro.ENTRADA_INVALIDA, "Chave de idempotência inválida"),
  CHAVE_DE_IDEMPOTENCIA_REUSADA(TipoDeErro.CONFLITO, "Chave de idempotência reusada"),
  REQUISICAO_EM_ANDAMENTO(TipoDeErro.CONFLITO, "Requisição em andamento"),
  ERRO_INTERNO(TipoDeErro.INTERNO, "Erro interno");

  private final TipoDeErro tipo;
  private final String titulo;

  CodigoWeb(TipoDeErro tipo, String titulo) {
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
