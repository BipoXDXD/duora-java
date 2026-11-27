// Classes repetidas em mais de um componente, convertidas de css/app.css declaração por declaração.
// O nome antigo (botao-primario...) fica na frente: é gancho de teste e de leitura, sem CSS próprio.
//
// hover: é só :hover (tokens.css); o not-aria-* faz o hover ceder ao estado, como na cascata antiga.

/** Alto contraste: o estado indisponível não pode depender só de opacidade ou cor de fundo. */
export const INDISPONIVEL_EM_ALTO_CONTRASTE =
  "forced-colors:aria-disabled:text-[color:GrayText] forced-colors:aria-disabled:border-[color:GrayText]";

export const BOTAO_PRIMARIO = [
  "botao-primario min-h-[52px] px-(--e-5) py-(--e-3) border-0 rounded-(--raio) bg-ambar text-ambar-tinta",
  "text-[1.0625rem] font-semibold cursor-pointer [transition:background_var(--transicao)] hover:not-aria-disabled:bg-ambar-claro",
  "aria-disabled:bg-noite-700 aria-disabled:text-texto-3 aria-disabled:cursor-not-allowed",
  INDISPONIVEL_EM_ALTO_CONTRASTE,
].join(" ");

/** Sem a margem de cima: dentro da confirmação de saída e do convite (o gap da grade já separa). */
export const BOTAO_SECUNDARIO_SEM_MARGEM = [
  "botao-secundario justify-self-start min-h-[44px] px-(--e-4) py-(--e-2) border border-ceu-400 rounded-(--raio)",
  "[background:none] cursor-pointer aria-disabled:opacity-70 aria-disabled:cursor-progress",
  INDISPONIVEL_EM_ALTO_CONTRASTE,
].join(" ");

export const BOTAO_SECUNDARIO = `${BOTAO_SECUNDARIO_SEM_MARGEM} mt-(--e-2)`;

/** Base de .botao-som e .botao-sair: ação rebaixada, só texto sublinhado. */
export const BOTAO_TEXTO =
  "justify-self-start min-h-[44px] px-0 py-(--e-2) border-0 [background:none] text-texto-2 underline underline-offset-3 cursor-pointer";

export const BOTAO_PERIGO = [
  "botao-perigo min-h-[44px] px-(--e-4) py-(--e-2) border-0 rounded-(--raio) bg-coral text-noite-950 font-semibold",
  "cursor-pointer aria-disabled:opacity-70 aria-disabled:cursor-progress",
  INDISPONIVEL_EM_ALTO_CONTRASTE,
].join(" ");

export const SUBTITULO = "subtitulo mx-0 mt-0 mb-(--e-2) text-texto-3 text-[0.875rem] font-semibold";

/**
 * Título que recebe foco do script: o anel só aparece quando o foco veio do teclado (:focus-visible);
 * depois de um toque ou clique, o título focado fica sem contorno.
 */
export const TITULO_DE_RESULTADO = [
  "resultado-titulo mx-0 mt-0 mb-(--e-2) font-titulo font-semibold text-[1.5rem] leading-[1.2]",
  "focus:not-focus-visible:[outline:none]",
  "focus-visible:[outline:2px_solid_transparent] focus-visible:outline-offset-2 focus-visible:[box-shadow:var(--foco)]",
].join(" ");

/** Base do <main className="painel">: o painel do app e o da tela de erro do cenário. */
export const CLASSES_DO_PAINEL =
  "painel [grid-area:painel] flex flex-col gap-(--e-5) px-(--e-6) pt-(--e-5) pb-(--e-6) bg-noite-800 celular:px-(--e-4) celular-deitado:px-(--e-4)";

/** Seções de uma tela só (entrada, convite, encerrado); no celular vêm logo depois da instrução. */
export const TELA = "grid gap-(--e-4) celular:order-2 celular-deitado:order-2";

export const CAMPO = "campo grid gap-(--e-2)";
export const ROTULO = "font-semibold";
export const ENTRADA_DE_TEXTO = [
  "w-full min-h-[48px] px-(--e-3) py-(--e-2) border border-ceu-600 rounded-(--raio) bg-noite-950 text-marfim",
  "[font:inherit] read-only:text-texto-2 read-only:text-[0.875rem] aria-invalid:border-coral",
].join(" ");
export const DICA = "dica m-0 text-[0.875rem] leading-[1.45] text-texto-3 max-w-[40ch]";
export const TEXTO_SECUNDARIO = "m-0 text-texto-2";

/** Título das telas da casca de conta. */
export const TITULO_DA_CASCA = "m-0 font-titulo font-semibold text-[2rem] leading-[1.15] tracking-[-0.01em]";

/** Link para o próximo passo numa tela da casca; sem margem, que cada tela dá a sua. */
export const LINK_DE_ACAO = [
  "inline-flex items-center min-h-[44px] text-ambar text-[1.0625rem] font-semibold underline underline-offset-4",
  "hover:text-ambar-claro [transition:color_var(--transicao)]",
].join(" ");

/** Um botão indisponível fica no foco e na leitura, mas ignora o clique. */
export function seDisponivel(indisponivel: boolean, acao: () => void): () => void {
  return () => {
    if (!indisponivel) {
      acao();
    }
  };
}
