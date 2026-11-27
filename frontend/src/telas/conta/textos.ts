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

export const CONFIRMAR = {
  titulo: "Confirmar entrada",
  tituloDaAba: "Confirmar entrada · Duora",
  /** O POST só sai no clique: um leitor de e-mail que pré-abre o link não gasta o token. */
  instrucao: "Toque em Entrar para abrir sua sessão neste navegador.",
  entrar: "Entrar",
  entrando: "Entrando…",
  semToken: "Não encontramos o código do link. Abra o link do e-mail de novo ou peça um novo.",
  pedirNovoLink: "Pedir novo link",
  /** Reservas para quando o servidor não manda a mensagem dele (ele sempre manda). */
  linkInvalido: "Este link expirou ou já foi usado. Peça um novo.",
  contaIndisponivel: "Esta conta não está disponível.",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar): o token pode não ter sido gasto. */
  falha: "Não conseguimos confirmar a entrada. Tente de novo em instantes.",
  /** A sessão abriu, mas a leitura da conta falhou: o token já foi gasto, então o caminho é o terraço. */
  falhaDepoisDaEntrada: "Sua entrada foi confirmada, mas a próxima página não carregou.",
  continuarNoTerraco: "Continuar no terraço",
} as const;

/** Texto do 429: o Retry-After em segundos vira minutos inteiros, arredondados para cima, nunca menos de 1. */
export function textoDeEspera(segundos: number): string {
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return `Muitas tentativas. Tente de novo em ${String(minutos)} ${minutos === 1 ? "minuto" : "minutos"}.`;
}
