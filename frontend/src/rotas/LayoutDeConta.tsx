import { Outlet, useMatches } from "react-router";
import { Rodape } from "../componentes/Rodape";

/** handle da rota de um texto longo (os documentos legais): a coluna abre para a largura de leitura. */
export const LARGURA_DE_LEITURA = { largura: "leitura" } as const;

function pedeLeitura(handle: unknown): boolean {
  return typeof handle === "object" && handle !== null && "largura" in handle && handle.largura === "leitura";
}

/**
 * Moldura das telas de conta: a mesma noite e as mesmas fontes do terraço, mas sem a cena nem o
 * topo do encontro. Coluna única de leitura, alinhada à esquerda, com respiro maior no alto. O rodapé
 * fica fora do main, no fim da coluna.
 */
export function LayoutDeConta() {
  const leitura = useMatches().some((rota) => pedeLeitura(rota.handle));
  return (
    <div className="min-h-dvh flex flex-col bg-noite-900 text-marfim px-(--e-5)">
      <div className={`mx-auto w-full flex-1 flex flex-col ${leitura ? "max-w-[68ch]" : "max-w-[40ch]"}`}>
        <main className="flex-1 pt-(--e-8) pb-(--e-7) celular:pt-(--e-7)">
          <Outlet />
        </main>
        <div className="pb-(--e-5)">
          <Rodape />
        </div>
      </div>
    </div>
  );
}
