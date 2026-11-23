import { Outlet } from "react-router";

/**
 * Moldura das telas de conta: a mesma noite e as mesmas fontes do terraço, mas sem a cena nem o
 * topo do encontro. Coluna única de leitura, alinhada à esquerda, com respiro maior no alto.
 */
export function LayoutDeConta() {
  return (
    <main className="min-h-dvh bg-noite-900 text-marfim px-(--e-5) pt-(--e-8) pb-(--e-7) celular:pt-(--e-7)">
      <div className="mx-auto max-w-[40ch]">
        <Outlet />
      </div>
    </main>
  );
}
