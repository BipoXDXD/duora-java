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

/**
 * Resumo SHA-256 (hex) do convite, o mesmo que o servidor publica em conviteDaSala. Sem Web Crypto
 * (HTTP fora de localhost) ou com o digest recusado, devolve null: quem chama não sabe de que sala
 * é o convite.
 */
export async function resumirConvite(token: string): Promise<string | null> {
  const subtle = (crypto as Partial<Crypto> | undefined)?.subtle;
  if (!subtle) {
    return null;
  }
  try {
    const resumo = await subtle.digest("SHA-256", new TextEncoder().encode(token));
    return Array.from(new Uint8Array(resumo), (b) => b.toString(16).padStart(2, "0")).join("");
  } catch {
    return null;
  }
}
