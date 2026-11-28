// As notificações do Duora (H4): o push traz só {"tipo":"..."}, e título, corpo, tag e destino são
// textos fixos daqui. Nenhum outro campo do payload chega à notificação: a tela bloqueada não mostra
// nada pessoal, e o clique nunca abre um endereço vindo de fora.

/** A mensagem que a página manda ao worker para saber se ele já trata push (resposta em ports[0]). */
export const MENSAGEM_DE_CAPACIDADES = "duora.capacidades";

const TITULO = "Duora";
const ICONE = "/assets/icones/icone-192.png";

/** O renotify ainda vale no Chrome, mas saiu do NotificationOptions do TypeScript. */
export interface OpcoesDaNotificacao extends NotificationOptions {
  body: string;
  tag: string;
  renotify: boolean;
  icon: string;
  data: { url: string };
}

export interface NotificacaoDoDuora {
  titulo: string;
  opcoes: OpcoesDaNotificacao;
}

interface TextosDoTipo {
  corpo: string;
  tag: string;
  url: string;
}

const POR_TIPO: Readonly<Record<string, TextosDoTipo>> = {
  compativel: { corpo: "Alguém compatível está esperando agora.", tag: "duora-compativel", url: "/encontrar" },
};

const GENERICA: TextosDoTipo = { corpo: "Abra o Duora para ver a novidade.", tag: "duora", url: "/" };

/** Monta a notificação a partir do texto do push; vazio, inválido ou tipo desconhecido vira a genérica. */
export function notificacaoDoPush(texto: string | null): NotificacaoDoDuora {
  const { corpo, tag, url } = textosDoTipo(lerTipo(texto));
  return { titulo: TITULO, opcoes: { body: corpo, tag, renotify: false, icon: ICONE, data: { url } } };
}

function lerTipo(texto: string | null): string | null {
  if (!texto) {
    return null;
  }
  let dados: unknown;
  try {
    dados = JSON.parse(texto);
  } catch {
    return null;
  }
  if (typeof dados !== "object" || dados === null || !("tipo" in dados) || typeof dados.tipo !== "string") {
    return null;
  }
  return dados.tipo;
}

function textosDoTipo(tipo: string | null): TextosDoTipo {
  return tipo !== null && Object.hasOwn(POR_TIPO, tipo) ? (POR_TIPO[tipo] ?? GENERICA) : GENERICA;
}

/**
 * Só um caminho desta origem: começa com "/" e não com "//" nem "/\" (endereço de outro host). Um
 * caractere de controle também recusa: o URL parser descarta tab e quebra de linha, e "/\t/x" viraria
 * "//x". O resto vira "/".
 */
export function urlSegura(url: unknown): string {
  if (typeof url !== "string" || !url.startsWith("/") || url.startsWith("//") || url.startsWith("/\\")) {
    return "/";
  }
  // eslint-disable-next-line no-control-regex -- recusar caracteres de controle é o objetivo
  return /[\u0000-\u001F\u007F]/.test(url) ? "/" : url;
}

/** A janela desta origem já no caminho de destino (a query não conta); outra janela não serve. */
export function janelaParaFocar<T extends { url: string }>(
  janelas: readonly T[],
  destino: string,
  origem: string,
): T | undefined {
  const caminho = new URL(destino, origem).pathname;
  return janelas.find((janela) => {
    const url = new URL(janela.url);
    return url.origin === origem && url.pathname === caminho;
  });
}

/** O corpo do PUT /api/push/inscricao (o mesmo de api/push.ts). */
export interface CorpoDaInscricao {
  endpoint: string;
  chaves: { p256dh: string; auth: string };
}

/** Converte o toJSON() da inscrição; sem endpoint ou sem as duas chaves, null. */
export function corpoDaInscricao(json: PushSubscriptionJSON): CorpoDaInscricao | null {
  const p256dh = json.keys?.["p256dh"];
  const auth = json.keys?.["auth"];
  if (!json.endpoint || !p256dh || !auth) {
    return null;
  }
  return { endpoint: json.endpoint, chaves: { p256dh, auth } };
}
