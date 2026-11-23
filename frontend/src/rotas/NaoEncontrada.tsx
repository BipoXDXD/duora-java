/**
 * Endereço fora das rotas da casca. O link volta ao terraço com recarga completa (um <a> comum, não
 * o Link do router): "/" é o app do encontro, que a casca não monta.
 */
export function NaoEncontrada() {
  return (
    <>
      <title>Página não encontrada · Duora</title>
      <h1 className="m-0 font-titulo font-semibold text-[2rem] leading-[1.15] tracking-[-0.01em]">
        Página não encontrada
      </h1>
      <p className="mt-(--e-4) mb-0 text-texto-2 text-[1.125rem] leading-[1.5]">
        Este endereço não leva a nenhuma página do Duora. Confira se o link chegou completo.
      </p>
      <a
        href="/"
        className="mt-(--e-6) inline-flex items-center min-h-[44px] text-ambar text-[1.0625rem] font-semibold underline underline-offset-4 hover:text-ambar-claro [transition:color_var(--transicao)]"
      >
        Voltar ao terraço
      </a>
    </>
  );
}
