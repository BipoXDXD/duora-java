package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Recusa;
import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Códigos de erro do terraço, com o tipo e o título de cada um. */
enum Codigo implements CodigoDeErro {
  SEM_ASSENTO(TipoDeErro.NAO_AUTENTICADO, "Sem assento"),
  SALA_INEXISTENTE(TipoDeErro.NAO_ENCONTRADO, "Sala inexistente"),
  CONVITE_INVALIDO(TipoDeErro.NAO_ENCONTRADO, "Convite inválido"),
  SALA_ENCERRADA(TipoDeErro.ENCERRADO, "Sala encerrada"),
  PECA_ALHEIA(TipoDeErro.PROIBIDO, "Peça alheia"),
  LUGAR_OCUPADO(TipoDeErro.CONFLITO, "Lugar ocupado"),
  VERSAO_OBSOLETA(TipoDeErro.CONFLITO, "Versão obsoleta"),
  ESPERE_PRIMEIRA_PECA(TipoDeErro.CONFLITO, "Espere a primeira peça"),
  FASE_INVALIDA(TipoDeErro.CONFLITO, "Fase inválida"),
  INCOMPLETA(TipoDeErro.CONFLITO, "Composição incompleta"),
  ABERTURA_EM_ANDAMENTO(TipoDeErro.CONFLITO, "Abertura em andamento"),
  LUGAR_INVALIDO(TipoDeErro.REGRA_DE_NEGOCIO, "Lugar inválido"),
  PECA_INVALIDA(TipoDeErro.REGRA_DE_NEGOCIO, "Peça inválida"),
  APELIDO_INVALIDO(TipoDeErro.REGRA_DE_NEGOCIO, "Apelido inválido"),
  CENARIO_INVALIDO(TipoDeErro.REGRA_DE_NEGOCIO, "Cenário inválido"),
  LIMITE_SALAS(TipoDeErro.INDISPONIVEL, "Limite de salas");

  private final TipoDeErro tipo;
  private final String titulo;

  Codigo(TipoDeErro tipo, String titulo) {
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

  static Codigo de(Recusa recusa) {
    return switch (recusa) {
      case PECA_ALHEIA -> PECA_ALHEIA;
      case LUGAR_INVALIDO -> LUGAR_INVALIDO;
      case LUGAR_OCUPADO -> LUGAR_OCUPADO;
      case VERSAO_OBSOLETA -> VERSAO_OBSOLETA;
      case ESPERE_PRIMEIRA_PECA -> ESPERE_PRIMEIRA_PECA;
      case FASE_INVALIDA -> FASE_INVALIDA;
      case INCOMPLETA -> INCOMPLETA;
      case ABERTURA_EM_ANDAMENTO -> ABERTURA_EM_ANDAMENTO;
    };
  }
}
