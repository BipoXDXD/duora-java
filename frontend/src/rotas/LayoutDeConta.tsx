import { useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useCallback } from "react";
import { Link, Outlet, useMatches } from "react-router";
import { Rodape } from "../componentes/Rodape";
import { consultaDasVersoesLegais } from "./consultas";

/** handle da rota de um texto longo (os documentos legais): a coluna abre para a largura de leitura. */
export const LARGURA_DE_LEITURA = { largura: "leitura" } as const;

function pedeLeitura(handle: unknown): boolean {
  return typeof handle === "object" && handle !== null && "largura" in handle && handle.largura === "leitura";
}

/** Na casca, o rodapé navega pelo roteador: sair do cadastro para os Termos não recarrega a página. */
function LinkDaCasca({ href, className, children }: { href: string; className: string; children: ReactNode }) {
  return (
    <Link to={href} className={className}>
      {children}
    </Link>
  );
}

/**
 * Moldura das telas de conta: a mesma noite e as mesmas fontes do terraço, mas sem a cena nem o
 * topo do encontro. Coluna única de leitura, alinhada à esquerda, com respiro maior no alto. O rodapé
 * fica fora do main, no fim da coluna, e lê o contato do mesmo cache das versões que o cadastro usa.
 */
export function LayoutDeConta() {
  const leitura = useMatches().some((rota) => pedeLeitura(rota.handle));
  const clienteDeConsulta = useQueryClient();
  const obterContato = useCallback(
    async () => (await clienteDeConsulta.query(consultaDasVersoesLegais)).contato,
    [clienteDeConsulta],
  );
  return (
    <div className="min-h-dvh flex flex-col bg-noite-900 text-marfim px-(--e-5)">
      <div className={`mx-auto w-full flex-1 flex flex-col ${leitura ? "max-w-[68ch]" : "max-w-[40ch]"}`}>
        <main className="flex-1 pt-(--e-8) pb-(--e-7) celular:pt-(--e-7)">
          <Outlet />
        </main>
        <div className="pb-(--e-5)">
          <Rodape obterContato={obterContato} link={LinkDaCasca} />
        </div>
      </div>
    </div>
  );
}
