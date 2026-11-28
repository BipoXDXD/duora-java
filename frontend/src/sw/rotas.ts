import { CAMINHO_DA_VERIFICACAO } from "../rotas/caminhosDaCasca";

/** Como o service worker atende um pedido. */
export type Rota = "ignorar" | "navegacao" | "rede-guardando" | "cache-primeiro" | "rede";

export interface PedidoDoWorker {
  url: URL;
  /** A origem do próprio worker (`self.location.origin`). */
  origem: string;
  metodo: string;
  modo: RequestMode;
  temRange: boolean;
}

/**
 * Os chunks que só a verificação usa: o grupo verificacao-amplify do vite.config.ts (JS e CSS) e os
 * chunks com o nome dos módulos da tela (o Rolldown nomeia o chunk pelo módulo de entrada).
 */
const PREFIXOS_DA_VERIFICACAO = [
  "/app/verificacao-amplify-",
  "/app/DetectorAmplify-",
  "/app/DetectorFalso-",
  "/app/VerificacaoPagina-",
];

/**
 * Decide a rota de um pedido.
 * - Ignorados (o navegador segue sozinho): escrita, outra origem, a API (nunca há cache de /api),
 *   pedidos parciais (o áudio pede por faixa de bytes, e o Cache API não guarda resposta 206) e a
 *   verificação: a navegação para /verificacao precisa da CSP e da Permissions-Policy dela, que só o
 *   servidor manda (o shell guardado traz as de "/"), e os chunks da tela (o do Amplify tem vários MB)
 *   só servem a esse documento e não entram no cache.
 * - A navegação vem da rede, com o shell guardado de reserva: um HTML novo chega mesmo com o
 *   worker antigo no controle (sem skipWaiting).
 * - O JSON de /assets/ vem da rede e renova a cópia guardada: o JS novo não pode ler dados velhos.
 * - Os binários de /assets/ e os arquivos com hash de /app/ vêm do cache primeiro. Um /app/ novo
 *   ainda não guardado vem da rede e entra no cache; os antigos ficam até o activate do worker novo.
 */
export function rotaDoPedido({ url, origem, metodo, modo, temRange }: PedidoDoWorker): Rota {
  if (metodo !== "GET" || url.origin !== origem || url.pathname.startsWith("/api/") || temRange) {
    return "ignorar";
  }
  if (
    url.pathname === CAMINHO_DA_VERIFICACAO ||
    PREFIXOS_DA_VERIFICACAO.some((prefixo) => url.pathname.startsWith(prefixo))
  ) {
    return "ignorar";
  }
  if (modo === "navigate") {
    return "navegacao";
  }
  if (url.pathname.startsWith("/assets/") && url.pathname.endsWith(".json")) {
    return "rede-guardando";
  }
  if (url.pathname.startsWith("/assets/") || url.pathname.startsWith("/app/")) {
    return "cache-primeiro";
  }
  return "rede";
}
