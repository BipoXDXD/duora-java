package bipotheking.net.duora.room;

import bipotheking.net.duora.game.Recusa;
import org.springframework.http.HttpStatus;

/** Códigos de erro do contrato, com o status HTTP e o título de cada um. */
enum Codigo {
  SEM_ASSENTO(HttpStatus.UNAUTHORIZED, "Sem assento"),
  SALA_INEXISTENTE(HttpStatus.NOT_FOUND, "Sala inexistente"),
  CONVITE_INVALIDO(HttpStatus.NOT_FOUND, "Convite inválido"),
  SALA_ENCERRADA(HttpStatus.GONE, "Sala encerrada"),
  PECA_ALHEIA(HttpStatus.FORBIDDEN, "Peça alheia"),
  ORIGEM_RECUSADA(HttpStatus.FORBIDDEN, "Origem recusada"),
  HOST_RECUSADO(HttpStatus.MISDIRECTED_REQUEST, "Host recusado"),
  LUGAR_OCUPADO(HttpStatus.CONFLICT, "Lugar ocupado"),
  VERSAO_OBSOLETA(HttpStatus.CONFLICT, "Versão obsoleta"),
  ESPERE_PRIMEIRA_PECA(HttpStatus.CONFLICT, "Espere a primeira peça"),
  FASE_INVALIDA(HttpStatus.CONFLICT, "Fase inválida"),
  INCOMPLETA(HttpStatus.CONFLICT, "Composição incompleta"),
  ABERTURA_EM_ANDAMENTO(HttpStatus.CONFLICT, "Abertura em andamento"),
  LUGAR_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Lugar inválido"),
  PECA_INVALIDA(HttpStatus.UNPROCESSABLE_CONTENT, "Peça inválida"),
  APELIDO_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Apelido inválido"),
  CENARIO_INVALIDO(HttpStatus.UNPROCESSABLE_CONTENT, "Cenário inválido"),
  CORPO_INVALIDO(HttpStatus.BAD_REQUEST, "Corpo inválido"),
  LIMITE_SALAS(HttpStatus.SERVICE_UNAVAILABLE, "Limite de salas"),
  ERRO_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno");

  private final HttpStatus status;
  private final String titulo;

  Codigo(HttpStatus status, String titulo) {
    this.status = status;
    this.titulo = titulo;
  }

  HttpStatus status() {
    return status;
  }

  String titulo() {
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
