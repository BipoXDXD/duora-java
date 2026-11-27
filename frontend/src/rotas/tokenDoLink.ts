const CAMINHO_DA_CONFIRMACAO = "/entrar/confirmar";

/**
 * Lê o token de #t= do link mágico e tira o fragmento da barra de endereço (o replaceState não
 * dispara hashchange). Só em /entrar/confirmar: nas outras telas da casca o fragmento é âncora e fica.
 * A casca chama uma vez, antes do primeiro render e fora do React, então a dupla montagem do
 * StrictMode nunca encontra a URL já limpa.
 */
export function lerTokenDoLink(local: Location, historia: History): string | null {
  if (local.pathname !== CAMINHO_DA_CONFIRMACAO) {
    return null;
  }
  const token = new URLSearchParams(local.hash.slice(1)).get("t");
  if (local.hash) {
    historia.replaceState(null, "", local.pathname + local.search);
  }
  return token || null;
}
