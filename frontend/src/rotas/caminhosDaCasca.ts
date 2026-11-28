/**
 * A prova de vida: sempre num documento próprio, com a CSP e a Permissions-Policy dela (o back a
 * espelha em web/RotasDoSpa.CAMINHO_DA_VERIFICACAO). Fica fora do rotas.tsx para o pwa.ts, no JS
 * inicial, lê-la sem baixar o roteador.
 */
export const CAMINHO_DA_VERIFICACAO = "/verificacao";

/**
 * Fonte única, no front, das rotas da casca de conta; o back espelha a lista em web/RotasDoSpa.CAMINHOS.
 * Nenhuma começa por /app/, reservado aos bundles com cache imutável.
 */
export const CAMINHOS_DA_CASCA = [
  "/entrar",
  "/entrar/confirmar",
  "/cadastro",
  CAMINHO_DA_VERIFICACAO,
  "/conta",
  "/termos",
  "/privacidade",
  "/denunciar",
  "/encarregado",
  "/perfil",
  "/perfil/preferencias",
  "/perfil/namoro",
  "/perfil/orientacao",
] as const;

export type CaminhoDaCasca = (typeof CAMINHOS_DA_CASCA)[number];

/**
 * Prefixos de rotas da casca com um segmento variável depois (o id da conta no perfil de outra
 * pessoa); o back espelha a lista em web/RotasDoSpa.PREFIXOS.
 */
export const PREFIXOS_DA_CASCA = ["/perfis/"] as const;
