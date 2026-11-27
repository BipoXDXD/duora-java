import type { QueryClient } from "@tanstack/react-query";
import type { RouteObject } from "react-router";
import type { DadosDaConfirmacao } from "../telas/conta/ConfirmarEntrada";
import { ErroDaConta } from "./ErroDaConta";
import { exigirConta } from "./guardas";
import { CarregandoDaCasca, LARGURA_DE_LEITURA, LayoutDeConta } from "./LayoutDeConta";
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
 * seu chunk). A raiz responde pelas bordas de todas as rotas: o `HydrateFallback` mostra a moldura
 * com o aviso de espera enquanto a primeira tela (guarda e chunk) chega, e o `ErrorBoundary` cobre
 * a falha de qualquer rota (guarda, download do chunk ou render da tela), também na moldura. O contexto chega às telas que precisam do cliente de consulta ou do token do link. As
 * rotas que exigem conta têm o guarda no loader, fora do chunk da tela. O React Router roda o loader
 * e o `lazy` em paralelo: o chunk pode baixar mesmo quando o guarda redireciona, mas a tela só
 * aparece depois que o guarda a libera.
 */
export function criarRotas({ clienteDeConsulta, tomarTokenDoLink }: ContextoDaCasca): RouteObject[] {
  return [
    {
      path: "/",
      Component: LayoutDeConta,
      HydrateFallback: CarregandoDaCasca,
      ErrorBoundary: ErroDaConta,
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
        },
        {
          path: "aguardando",
          loader: () => exigirConta(clienteDeConsulta, ["AGUARDANDO_VERIFICACAO"]),
          lazy: async () => ({ Component: (await import("../telas/conta/Aguardando")).Aguardando }),
        },
        {
          path: "conta",
          loader: () => exigirConta(clienteDeConsulta, ["AGUARDANDO_VERIFICACAO", "ATIVA"]),
          lazy: async () => ({ Component: (await import("../telas/conta/MinhaConta")).MinhaConta }),
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
        {
          path: "denunciar",
          lazy: async () => ({ Component: (await import("../telas/legal/DenunciarPublico")).DenunciarPublico }),
        },
        {
          path: "encarregado",
          lazy: async () => ({ Component: (await import("../telas/legal/Encarregado")).Encarregado }),
        },
        { path: "*", Component: NaoEncontrada },
      ],
    },
  ];
}
