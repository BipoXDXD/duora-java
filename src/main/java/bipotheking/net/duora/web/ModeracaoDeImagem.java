package bipotheking.net.duora.web;

import java.nio.file.Path;

/**
 * A moderação automática de uma imagem enviada, antes de ela aparecer para alguém. Quem implementa
 * não deve lançar: qualquer falha (rede, prazo, limitação, acesso negado, configuração ausente) é
 * {@link Veredito#INDISPONIVEL}, e só {@link Veredito#LIBERADA} dispensa a revisão humana.
 */
public interface ModeracaoDeImagem {

  /**
   * Avalia a imagem.
   *
   * @param jpeg a cópia em JPEG da nítida ({@link ImagemProcessada#paraModeracao()})
   */
  Veredito avaliar(Path jpeg);

  /** O desfecho da moderação automática. */
  enum Veredito {
    /** O fornecedor respondeu e nenhum rótulo bloqueado passou do limiar. */
    LIBERADA,
    /** Algum rótulo bloqueado passou do limiar: a imagem espera a revisão humana. */
    SINALIZADA,
    /** Sem resposta do fornecedor (ou sem fornecedor): a imagem espera a revisão humana. */
    INDISPONIVEL
  }
}
