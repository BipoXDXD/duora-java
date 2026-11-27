import { vi } from "vitest";

/** As versões legais que o rodapé lê para o link de Contato. */
export const VERSOES_LEGAIS = {
  termos: { versao: "2026-11-22" },
  privacidade: { versao: "2026-11-22" },
  contato: "teste@exemplo.com",
  encarregado: { nome: "o responsável pelo Duora", email: "encarregado@exemplo.invalid" },
};

/** Pedido que nunca recebe resposta: a tela fica no estado de espera. */
export const semResposta: typeof fetch = () => new Promise<Response>(() => {});

/**
 * Troca o fetch global por um falso. GET /api/legal/versoes (o Contato do rodapé) responde
 * VERSOES_LEGAIS sem passar por `resto`; o resto dos pedidos vai para `resto`, que por padrão nunca
 * responde. Quem chama desfaz com vi.unstubAllGlobals().
 */
export function stubarFetch(resto: typeof fetch = semResposta) {
  const fetchFalso = vi.fn<typeof fetch>((entrada, opcoes) =>
    entrada === "/api/legal/versoes"
      ? Promise.resolve(
          new Response(JSON.stringify(VERSOES_LEGAIS), { headers: { "Content-Type": "application/json" } }),
        )
      : resto(entrada, opcoes),
  );
  vi.stubGlobal("fetch", fetchFalso);
  return fetchFalso;
}
