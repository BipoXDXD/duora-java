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

/** Título que recebe foco do script: o anel aparece mesmo sem :focus-visible (Firefox e Safari). */
export const TITULO_DE_RESULTADO = [
  "resultado-titulo mx-0 mt-0 mb-(--e-2) font-titulo font-semibold text-[1.5rem] leading-[1.2]",
  "focus:[outline:2px_solid_transparent] focus:outline-offset-2 focus:[box-shadow:var(--foco)]",
].join(" ");

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

/** Um botão indisponível fica no foco e na leitura, mas ignora o clique. */
export function seDisponivel(indisponivel: boolean, acao: () => void): () => void {
  return () => {
    if (!indisponivel) {
      acao();
    }
  };
}
