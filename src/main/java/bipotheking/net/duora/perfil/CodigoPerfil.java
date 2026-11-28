package bipotheking.net.duora.perfil;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/** Recusas do perfil e das preferências. Cada código traz a mensagem do contrato. */
public enum CodigoPerfil implements CodigoDeErro {
  APELIDO_INVALIDO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Apelido inválido",
      "O apelido precisa ter de 1 a 20 caracteres."),
  BIO_INVALIDA(TipoDeErro.REGRA_DE_NEGOCIO, "Bio inválida", "A bio pode ter até 280 caracteres."),
  INTENCAO_INVALIDA(
      TipoDeErro.REGRA_DE_NEGOCIO, "Intenção inválida", "Escolha amizade, namoro ou os dois."),
  PERFIL_NECESSARIO(
      TipoDeErro.REGRA_DE_NEGOCIO, "Perfil necessário", "Salve seu perfil antes de continuar."),
  FAIXA_ETARIA_INVALIDA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Faixa etária inválida",
      "Escolha idades de 18 a 99, com a primeira até a segunda."),
  LOCALIZACAO_NECESSARIA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Localização necessária",
      "Ative a localização para usar um raio."),
  INTENCAO_SEM_NAMORO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Intenção sem namoro",
      "Marque Namoro em Como você quer usar o Duora para preencher estes dados."),
  CONSENTIMENTO_NECESSARIO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Consentimento necessário",
      "Marque a caixa de consentimento para salvar estes dados."),
  GENERO_INVALIDO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Gênero inválido",
      "Escolha seu gênero e ao menos uma opção de quem conhecer. Em Prefiro descrever, use de 1 a"
          + " 40 caracteres."),
  IDENTIDADE_INVALIDA(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Identidade inválida",
      "Em Prefiro descrever, use de 1 a 40 caracteres na orientação e de 1 a 20 nos pronomes."),
  VERSAO_OBSOLETA(
      TipoDeErro.CONFLITO,
      "Versão obsoleta",
      "Seu perfil mudou enquanto você editava. Confira e salve de novo."),
  PERFIL_INEXISTENTE(
      TipoDeErro.NAO_ENCONTRADO, "Perfil inexistente", "Não encontramos este perfil.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoPerfil(TipoDeErro tipo, String titulo, String mensagem) {
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
