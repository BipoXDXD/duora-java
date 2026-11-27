import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import { ErroHttp } from "../api/http";
import { criarRotas } from "./rotas";
import { lerTokenDoLink } from "./tokenDoLink";

const MAXIMO_DE_TENTATIVAS_EXTRAS = 2;

/** Só falha de rede ou do servidor merece outra tentativa, e no máximo duas; erro de negócio não muda repetindo. */
export function tentarDeNovo(falhas: number, erro: unknown): boolean {
  return erro instanceof ErroHttp && erro.transitorio && falhas < MAXIMO_DE_TENTATIVAS_EXTRAS;
}

/**
 * Monta a casca de conta na raiz: React Router no modo de dados e TanStack Query. Carregado por
 * import() a partir do despacho, fora do JS inicial do terraço. O #t= do link mágico é lido aqui, uma
 * vez e antes do primeiro render, e sai da URL; a rota de confirmação o toma uma vez só.
 */
export function montarCasca(raiz: HTMLElement, local: Location, historia: History): Root {
  let tokenDoLink = lerTokenDoLink(local, historia);
  const tomarTokenDoLink = () => {
    const token = tokenDoLink;
    tokenDoLink = null;
    return token;
  };
  // Sem pausa fora do ar: o navigator.onLine erra (portal cativo, VPN), e uma consulta ou envio
  // pausado deixaria o guarda ou o botão parados. A falha de rede vira o aviso ou a repetição.
  const clienteDeConsulta = new QueryClient({
    defaultOptions: {
      queries: { retry: tentarDeNovo, networkMode: "always" },
      mutations: { networkMode: "always" },
    },
  });
  const roteador = createBrowserRouter(criarRotas({ clienteDeConsulta, tomarTokenDoLink }));
  const root = createRoot(raiz);
  root.render(
    <StrictMode>
      <QueryClientProvider client={clienteDeConsulta}>
        <RouterProvider router={roteador} />
      </QueryClientProvider>
    </StrictMode>,
  );
  return root;
}
