import type { QueryClient } from "@tanstack/react-query";
import { type LoaderFunctionArgs, redirectDocument, type RouteObject } from "react-router";
import type { DadosDaConfirmacao } from "../telas/conta/ConfirmarEntrada";
import type { DadosDaVerificacao } from "../telas/verificacao/VerificacaoPagina";
import { CAMINHO_DA_VERIFICACAO } from "./caminhosDaCasca";
import { consultaDasVersoesLegais, consultaDaVerificacao } from "./consultas";
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
  /** O pathname em que o documento foi carregado; decide o isolamento da verificação. */
  caminhoDaCarga: string;
}

export { CAMINHO_DA_VERIFICACAO };

/** Loader que troca a navegação pelo roteador por uma carga nova do documento, no mesmo endereço. */
function recarregarODocumento({ request }: LoaderFunctionArgs): never {
  const { pathname, search } = new URL(request.url);
  throw redirectDocument(pathname + search);
}

/**
 * A verificação vive num documento próprio: só ele recebe a CSP e a Permissions-Policy da prova de
 * vida, e só por ele o detector carrega. Num documento aberto em outro caminho, a navegação pelo
 * roteador até a verificação recarrega o documento; no documento da verificação, a navegação para
 * qualquer outra rota (com ou sem loader) também. A rota trocada fica só com o path e o loader da
 * recarga; quem fica no documento certo não muda.
 */
export function isolarDocumentoDaVerificacao(filhas: RouteObject[], caminhoDaCarga: string): RouteObject[] {
  const cargaNaVerificacao = caminhoDaCarga === CAMINHO_DA_VERIFICACAO;
  return filhas.map((filha) => {
    const ehVerificacao = `/${filha.path ?? ""}` === CAMINHO_DA_VERIFICACAO;
    // Só o path e o loader: com o lazy, o roteador esperaria o chunk de uma tela que este documento
    // nunca mostra antes de recarregar, e a falha desse download viraria o erro da casca.
    return ehVerificacao === cargaNaVerificacao ? filha : { path: filha.path, loader: recarregarODocumento };
  });
}

/**
 * A árvore da casca: o layout de conta por fora e, dentro dele, cada tela com `lazy` (cada uma no
 * seu chunk). A raiz responde pelas bordas de todas as rotas: o `HydrateFallback` mostra a moldura
 * com o aviso de espera enquanto a primeira tela (guarda e chunk) chega, e o `ErrorBoundary` cobre
 * a falha de qualquer rota (guarda, download do chunk ou render da tela), também na moldura. O contexto chega às telas que precisam do cliente de consulta ou do token do link. As
 * rotas que exigem conta têm o guarda no loader, fora do chunk da tela. O React Router roda o loader
 * e o `lazy` em paralelo: o chunk pode baixar mesmo quando o guarda redireciona, mas a tela só
 * aparece depois que o guarda a libera. As filhas passam pelo isolamento do documento da verificação.
 */
export function criarRotas({ clienteDeConsulta, tomarTokenDoLink, caminhoDaCarga }: ContextoDaCasca): RouteObject[] {
  return [
    {
      path: "/",
      Component: LayoutDeConta,
      HydrateFallback: CarregandoDaCasca,
      ErrorBoundary: ErroDaConta,
      children: isolarDocumentoDaVerificacao(
        [
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
            path: "verificacao",
            loader: async (): Promise<DadosDaVerificacao> => ({
              conta: await exigirConta(clienteDeConsulta, ["AGUARDANDO_VERIFICACAO"]),
              situacao: await clienteDeConsulta.query(consultaDaVerificacao),
              legal: await clienteDeConsulta.query(consultaDasVersoesLegais),
            }),
            lazy: async () => ({
              Component: (await import("../telas/verificacao/VerificacaoPagina")).VerificacaoPagina,
            }),
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
        caminhoDaCarga,
      ),
    },
  ];
}
