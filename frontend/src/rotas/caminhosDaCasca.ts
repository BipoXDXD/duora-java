/**
 * Fonte única, no front, das rotas da casca de conta; o back espelha a lista em web/RotasDoSpa.CAMINHOS.
 * Nenhuma começa por /app/, reservado aos bundles com cache imutável.
 */
export const CAMINHOS_DA_CASCA = [
  "/entrar",
  "/entrar/confirmar",
  "/cadastro",
  "/aguardando",
  "/conta",
  "/termos",
  "/privacidade",
  "/denunciar",
  "/encarregado",
] as const;

export type CaminhoDaCasca = (typeof CAMINHOS_DA_CASCA)[number];
