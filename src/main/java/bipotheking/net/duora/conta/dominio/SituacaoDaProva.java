package bipotheking.net.duora.conta.dominio;

/**
 * Situação da sessão de prova de vida no fornecedor. O adapter traduz os estados dele ({@code
 * CREATED} … {@code EXPIRED}); um estado desconhecido vira {@link #FALHOU}.
 */
public enum SituacaoDaProva {
  CRIADA,
  EM_ANDAMENTO,
  CONCLUIDA,
  FALHOU,
  EXPIRADA
}
