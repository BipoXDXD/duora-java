package bipotheking.net.duora.conta.dominio;

import java.util.UUID;

/** Porta da prova de vida com estimativa de idade pela selfie. */
public interface ProvaDeVida {

  /**
   * Quem atende: {@link ModoDeCaptura#AWS} no adapter real, {@link ModoDeCaptura#FALSO} no falso.
   */
  ModoDeCaptura modo();

  /**
   * Abre uma sessão de prova de vida no fornecedor.
   *
   * @param tentativa id da tentativa de verificação; serve de chave de idempotência no fornecedor
   * @throws FornecedorIndisponivel se o fornecedor falhar
   */
  SessaoDeProva criarSessao(UUID tentativa);

  /**
   * Lê o resultado da sessão, com a imagem de referência quando houver. Quem decide pela selfie
   * descarta a referência ao sair do método.
   *
   * @throws FornecedorIndisponivel se o fornecedor falhar
   */
  ResultadoDaProva resultado(String sessaoId);
}
