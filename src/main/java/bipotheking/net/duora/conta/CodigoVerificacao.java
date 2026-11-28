package bipotheking.net.duora.conta;

import bipotheking.net.duora.nucleo.CodigoDeErro;
import bipotheking.net.duora.nucleo.ErroDeNegocio;
import bipotheking.net.duora.nucleo.TipoDeErro;

/**
 * Recusas da verificação de idade. Cada código traz a mensagem do contrato. Menor de idade
 * reaproveita {@link CodigoCadastro#MENOR_DE_IDADE}.
 */
public enum CodigoVerificacao implements CodigoDeErro {
  CONSENTIMENTO_BIOMETRICO_NECESSARIO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Consentimento biométrico necessário",
      "Concorde com o uso da sua imagem para confirmar sua idade."),
  VERIFICACAO_FORA_DE_ORDEM(
      TipoDeErro.CONFLITO,
      "Verificação fora de ordem",
      "Esta etapa da verificação não está disponível agora. Recarregue a página."),
  SESSAO_DE_VERIFICACAO_INVALIDA(
      TipoDeErro.NAO_ENCONTRADO,
      "Sessão de verificação inválida",
      "Esta verificação expirou. Comece de novo."),
  REFAZER_PROVA_DE_VIDA(
      TipoDeErro.CONFLITO,
      "Refazer prova de vida",
      "A selfie venceu antes do envio. Faça uma nova."),
  SELFIE_NAO_DEU_CERTO(
      TipoDeErro.CONFLITO, "Selfie não deu certo", "A selfie não deu certo. Faça uma nova."),
  DOCUMENTO_INVALIDO(
      TipoDeErro.REGRA_DE_NEGOCIO,
      "Documento inválido",
      "Envie fotos JPEG ou PNG de até 5 MB, frente e verso."),
  LIMITE_DE_VERIFICACAO(
      TipoDeErro.LIMITE,
      "Limite de verificação",
      "Você chegou ao limite de tentativas de hoje. Tente de novo amanhã."),
  PROVA_DE_VIDA_INDISPONIVEL(
      TipoDeErro.INDISPONIVEL,
      "Prova de vida indisponível",
      "A verificação está fora do ar. Tente de novo em alguns minutos."),
  CONFERENCIA_DE_DOCUMENTO_INDISPONIVEL(
      TipoDeErro.INDISPONIVEL,
      "Conferência de documento indisponível",
      "A conferência do documento está fora do ar. Tente de novo em alguns minutos.");

  private final TipoDeErro tipo;
  private final String titulo;
  private final String mensagem;

  CodigoVerificacao(TipoDeErro tipo, String titulo, String mensagem) {
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
