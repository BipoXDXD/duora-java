import type { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router";
import { ErroDaConta } from "./ErroDaConta";
import { exigirConta } from "./guardas";
import { LayoutDeConta } from "./LayoutDeConta";
import { NaoEncontrada } from "./NaoEncontrada";

export interface ContextoDaCasca {
  clienteDeConsulta: QueryClient;
  /** O #t= do link mágico, lido uma vez antes do primeiro render; null fora de /entrar/confirmar. */
  tokenDoLink: string | null;
}

/**
 * A árvore da casca: o layout de conta por fora e, dentro dele, cada tela com `lazy` (cada uma no
 * seu chunk). O contexto chega às telas que precisam do cliente de consulta ou do token do link. As
 * rotas que exigem conta têm o guarda no loader, fora do chunk da tela: o redirect sai antes de
 * baixá-la.
 */
export function criarRotas({ clienteDeConsulta, tokenDoLink }: ContextoDaCasca): RouteObject[] {
  return [
    {
      path: "/",
      Component: LayoutDeConta,
      children: [
        {
          path: "entrar",
          lazy: async () => ({ Component: (await import("../telas/conta/Entrar")).Entrar }),
        },
        {
          path: "entrar/confirmar",
          lazy: async () => {
            const { ConfirmarEntrada } = await import("../telas/conta/ConfirmarEntrada");
            return { element: <ConfirmarEntrada token={tokenDoLink} /> };
          },
        },
        {
          path: "cadastro",
          loader: () => exigirConta(clienteDeConsulta, ["PENDENTE"]),
          lazy: async () => ({ Component: (await import("../telas/conta/Cadastro")).Cadastro }),
          ErrorBoundary: ErroDaConta,
        },
        {
          path: "aguardando",
          loader: () => exigirConta(clienteDeConsulta, ["AGUARDANDO_VERIFICACAO"]),
          lazy: async () => ({ Component: (await import("../telas/conta/Aguardando")).Aguardando }),
          ErrorBoundary: ErroDaConta,
        },
        { path: "*", Component: NaoEncontrada },
      ],
    },
  ];
}
