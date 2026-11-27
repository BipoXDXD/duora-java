import { type ComponentType, type ReactElement, type ReactNode, useEffect, useState } from "react";
import { obterVersoesLegais } from "../api/legal";

/** Link para uma página da casca: <a> comum no terraço; o Link do roteador na casca. */
export type LinkDoRodape = ComponentType<{ href: string; className: string; children: ReactNode }>;

interface PropsRodape {
  obterContato?: () => Promise<string>;
  link?: LinkDoRodape;
}

async function obterContatoDoServidor(): Promise<string> {
  return (await obterVersoesLegais()).contato;
}

function LinkComum({ href, className, children }: { href: string; className: string; children: ReactNode }) {
  return (
    <a href={href} className={className}>
      {children}
    </a>
  );
}

/** Alvo de toque de 44 px, como os outros links de texto do app. */
const LINK =
  "inline-flex items-center min-h-[44px] text-texto-2 underline underline-offset-3 hover:text-marfim [transition:color_var(--transicao)]";

/**
 * Termos, Privacidade, Denunciar, Encarregado de dados e Contato, na entrada do terraço e nas telas da
 * casca. Também roda no "/": por padrão usa só o fetch de http.ts (sem TanStack Query) e <a> comuns,
 * sem o roteador da casca; a casca injeta o contato do cache e o Link dela. O Contato espera o e-mail
 * do servidor e vem por último, para não empurrar os outros links quando chega; se ele não vier, o
 * rodapé segue sem ele. O mailto: é sempre um <a>.
 */
export function Rodape({ obterContato = obterContatoDoServidor, link: Link = LinkComum }: PropsRodape): ReactElement {
  const [contato, setContato] = useState<string | null>(null);

  useEffect(() => {
    let montado = true;
    obterContato().then(
      (lido) => {
        if (montado) {
          setContato(lido);
        }
      },
      (erro: unknown) => {
        // Sem contato, sem o link; os outros continuam. O Contato é o canal dos pedidos de
        // dados (LGPD): a falha, inclusive uma resposta 200 fora do contrato, fica no console.
        console.warn("Contato do rodapé indisponível:", erro);
      },
    );
    return () => {
      montado = false;
    };
  }, [obterContato]);

  return (
    <footer className="text-[0.875rem] leading-[1.4]">
      <ul className="m-0 p-0 list-none flex flex-wrap gap-x-(--e-5)">
        <li>
          <Link href="/termos" className={LINK}>
            Termos de uso
          </Link>
        </li>
        <li>
          <Link href="/privacidade" className={LINK}>
            Privacidade
          </Link>
        </li>
        <li>
          <Link href="/denunciar" className={LINK}>
            Denunciar
          </Link>
        </li>
        <li>
          <Link href="/encarregado" className={LINK}>
            Encarregado de dados
          </Link>
        </li>
        {contato !== null && (
          <li>
            <a href={`mailto:${contato}`} className={LINK}>
              Contato
            </a>
          </li>
        )}
      </ul>
    </footer>
  );
}
