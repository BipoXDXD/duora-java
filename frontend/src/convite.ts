/**
 * Lê o token de #convite= e tira o fragmento da barra de endereço (o replaceState não dispara
 * hashchange). Serve à primeira carga e a cada convite aberto depois na mesma aba.
 */
export function lerConvite(local: Location, historia: History): string | null {
  const convite = new URLSearchParams(local.hash.slice(1)).get("convite");
  if (local.hash) {
    historia.replaceState(null, "", local.pathname + local.search);
  }
  return convite || null;
}
