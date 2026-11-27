package bipotheking.net.duora.conta.dominio;

import java.util.UUID;

/** Porta que emite a credencial curta com que o navegador faz a captura da prova de vida. */
public interface CredencialDoNavegador {

  /**
   * Emite uma credencial temporária para a tentativa.
   *
   * @param tentativa id da tentativa de verificação; identifica a sessão da credencial
   * @throws FornecedorIndisponivel se o fornecedor falhar
   */
  CredencialCurta emitir(UUID tentativa);
}
