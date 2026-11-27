// Textos fixos das telas de conta. As mensagens de negócio (sucesso, e-mail recusado) vêm do servidor.

export const ENTRAR = {
  titulo: "Entrar no Duora",
  tituloDaAba: "Entrar · Duora",
  rotuloDoEmail: "Seu e-mail",
  enviar: "Enviar link",
  enviando: "Enviando…",
  aguardeAVerificacao: "Aguarde a verificação de segurança acima.",
  falhaDaVerificacao: "Não conseguimos carregar a verificação de segurança.",
  tentarDeNovo: "Tentar de novo",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar, 409): o link pode ter saído. */
  falhaNoEnvio: "Não conseguimos confirmar o envio do link. Tente de novo em instantes.",
} as const;

/** Texto do 429: o Retry-After em segundos vira minutos inteiros, arredondados para cima, nunca menos de 1. */
export function textoDeEspera(segundos: number): string {
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return `Muitas tentativas. Tente de novo em ${String(minutos)} ${minutos === 1 ? "minuto" : "minutos"}.`;
}
