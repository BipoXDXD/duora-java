import type { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router";
import type { DadosDaConfirmacao } from "../telas/conta/ConfirmarEntrada";
import { ErroDaConta } from "./ErroDaConta";
import { exigirConta } from "./guardas";
import { LARGURA_DE_LEITURA, LayoutDeConta } from "./LayoutDeConta";
import { NaoEncontrada } from "./NaoEncontrada";

export interface ContextoDaCasca {
  clienteDeConsulta: QueryClient;
  /**
   * Entrega o #t= do link mágico (lido antes do primeiro render) e o esquece: a segunda chamada
   * devolve null. Fora de /entrar/confirmar, sempre null.
   */
  tomarTokenDoLink: () => string | null;
}

/**
 * A árvore da casca: o layout de conta por fora e, dentro dele, cada tela com `lazy` (cada uma no
 * seu chunk). O contexto chega às telas que precisam do cliente de consulta ou do token do link. As
 * rotas que exigem conta têm o guarda no loader, fora do chunk da tela. O React Router roda o loader
 * e o `lazy` em paralelo: o chunk pode baixar mesmo quando o guarda redireciona, mas a tela só
 * aparece depois que o guarda a libera.
 */
export function criarRotas({ clienteDeConsulta, tomarTokenDoLink }: ContextoDaCasca): RouteObject[] {
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
          // O loader roda a cada chegada à rota, fora do React: só a primeira recebe o token. Quem
          // volta à tela pelo histórico (token já gasto ou em uso) cai no "sem token".
          loader: (): DadosDaConfirmacao => ({ token: tomarTokenDoLink() }),
          // Sem revalidar: uma nova leitura com a tela aberta trocaria o token por null.
          shouldRevalidate: () => false,
          lazy: async () => ({
            Component: (await import("../telas/conta/ConfirmarEntrada")).ConfirmarEntradaDaRota,
          }),
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
        {
          path: "termos",
          handle: LARGURA_DE_LEITURA,
          lazy: async () => ({ Component: (await import("../telas/legal/DocumentoLegalPagina")).TermosDeUso }),
        },
        {
          path: "privacidade",
          handle: LARGURA_DE_LEITURA,
          lazy: async () => ({
            Component: (await import("../telas/legal/DocumentoLegalPagina")).PoliticaDePrivacidade,
          }),
        },
        { path: "*", Component: NaoEncontrada },
      ],
    },
  ];
}
