import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { createBrowserRouter, RouterProvider } from "react-router";
import { ErroHttp } from "../api/http";
import { criarRotas } from "./rotas";

const MAXIMO_DE_TENTATIVAS_EXTRAS = 2;

/** Só falha de rede ou do servidor merece outra tentativa, e no máximo duas; erro de negócio não muda repetindo. */
export function tentarDeNovo(falhas: number, erro: unknown): boolean {
  return erro instanceof ErroHttp && erro.transitorio && falhas < MAXIMO_DE_TENTATIVAS_EXTRAS;
}

/**
 * Monta a casca de conta na raiz: React Router no modo de dados e TanStack Query. Carregado por
 * import() a partir do despacho, fora do JS inicial do terraço. Local e história já fazem parte da
 * assinatura do contrato: a F7 lê com eles, uma vez, o #t= do link mágico e limpa a URL (até lá,
 * _local e _historia ficam sem uso).
 */
export function montarCasca(raiz: HTMLElement, _local: Location, _historia: History): Root {
  const clienteDeConsulta = new QueryClient({
    defaultOptions: { queries: { retry: tentarDeNovo } },
  });
  const roteador = createBrowserRouter(criarRotas({ clienteDeConsulta, tokenDoLink: null }));
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
