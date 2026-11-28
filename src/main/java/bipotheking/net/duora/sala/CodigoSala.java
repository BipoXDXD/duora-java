package bipotheking.net.duora.sala;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas da fila e dos encontros. Cada código traz a mensagem do contrato. */
public enum CodigoSala implements CodigoDeErro {
  PERFIL_INCOMPLETO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Perfil incompleto",
      "Complete seu perfil em Meu perfil para entrar na fila."),
  CONTA_PAUSADA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Conta pausada",
      "Sua conta está pausada. Volte a aparecer em Meu perfil para entrar na fila."),
  JA_EM_ENCONTRO(
      TipoDeErro.CONFLITO,
      "Já em encontro",
      "Você já está num encontro. Termine esse antes de começar outro."),
  FORA_DA_FILA(TipoDeErro.NAO_ENCONTRADO, "Fora da fila", "Você não está na fila."),
  ENCONTRO_INEXISTENTE(
      TipoDeErro.NAO_ENCONTRADO, "Encontro inexistente", "Este encontro não existe mais."),
  CONVITE_INVALIDO(TipoDeErro.NAO_ENCONTRADO, "Convite inválido", "Este convite não vale mais."),
  CONVITE_PROPRIO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Convite próprio",
      "Este convite é seu. Mande o link para outra pessoa."),
  CAPACIDADE_CHEIA(
      TipoDeErro.INDISPONIVEL,
      "Capacidade cheia",
      "Todos os encontros estão ocupados. Tente de novo em 1 minuto."),
  CENARIO_INVALIDO(
      TipoDeErro.REGRA_DE_NEGOCIO, "Cenário inválido", "Escolha um dos cenários da lista.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoSala(TipoDeErro tipo, String titulo, String mensagem) {
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
