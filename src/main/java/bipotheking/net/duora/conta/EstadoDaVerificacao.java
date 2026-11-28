package bipotheking.net.duora.conta;

/**
 * Onde a conta está na verificação de idade, como o front a vê: {@code AGUARDANDO} sem decisão,
 * {@code DUVIDA} com selfie em dúvida nas últimas 24 h (cabe o documento), {@code ANALISE} com o
 * documento também em dúvida nas últimas 24 h (o envio não reabre; vale sobre {@code DUVIDA}),
 * {@code APROVADA} com a conta ativa.
 */
public enum EstadoDaVerificacao {
  AGUARDANDO,
  DUVIDA,
  ANALISE,
  APROVADA
}
