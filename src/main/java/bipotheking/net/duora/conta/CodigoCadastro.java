package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas do cadastro. Cada código traz a mensagem do contrato. */
public enum CodigoCadastro implements CodigoDeErro {
  DATA_INVALIDA(TipoDeErro.REGRA_DE_NEGOCIO, "Data inválida", "Confira a data de nascimento."),
  ACEITE_NECESSARIO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Aceite necessário",
      "Aceite os Termos de uso e a Política de privacidade para continuar."),
  TERMOS_DESATUALIZADOS(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Termos desatualizados",
      "Os termos mudaram. Recarregue a página e leia a versão nova."),
  MENOR_DE_IDADE(
      TipoDeErro.REGRA_DE_NEGOCIO, "Menor de idade", "O Duora é só para maiores de 18 anos."),
  CADASTRO_JA_CONCLUIDO(
      TipoDeErro.CONFLITO, "Cadastro já concluído", "O cadastro já foi concluído.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoCadastro(TipoDeErro tipo, String titulo, String mensagem) {
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
