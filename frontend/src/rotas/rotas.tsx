import type { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router";
import { LayoutDeConta } from "./LayoutDeConta";
import { NaoEncontrada } from "./NaoEncontrada";

export interface ContextoDaCasca {
  clienteDeConsulta: QueryClient;
  /** O #t= do link mágico, lido uma vez antes do primeiro render; null fora de /entrar/confirmar. */
  tokenDoLink: string | null;
}

/**
 * A árvore da casca: o layout de conta por fora e, dentro dele, cada tela com `lazy` (cada uma no
 * seu chunk). O contexto chega aos loaders das telas que precisam do cliente de consulta ou do token
 * (F7 em diante; até lá, _contexto fica sem uso).
 */
export function criarRotas(_contexto: ContextoDaCasca): RouteObject[] {
  return [
    {
      path: "/",
      Component: LayoutDeConta,
      children: [{ path: "*", Component: NaoEncontrada }],
    },
  ];
}
