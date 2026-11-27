import { type ReactElement, useEffect, useState } from "react";
import { obterVersoesLegais } from "../api/legal";

interface PropsRodape {
  obterContato?: () => Promise<string>;
}

async function obterContatoDoServidor(): Promise<string> {
  return (await obterVersoesLegais()).contato;
}

/** Alvo de toque de 44 px, como os outros links de texto do app. */
const LINK =
  "inline-flex items-center min-h-[44px] text-texto-2 underline underline-offset-3 hover:text-marfim [transition:color_var(--transicao)]";

/**
 * Termos, Privacidade e Contato, na entrada do terraço e nas telas da casca. Também roda no "/": usa
 * só o fetch de http.ts (sem TanStack Query) e <a> comuns, sem o roteador da casca. O Contato espera
 * o e-mail do servidor; se ele não vier, o rodapé segue com os dois documentos.
 */
export function Rodape({ obterContato = obterContatoDoServidor }: PropsRodape): ReactElement {
  const [contato, setContato] = useState<string | null>(null);

  useEffect(() => {
    let montado = true;
    obterContato().then(
      (lido) => {
        if (montado) {
          setContato(lido);
        }
      },
      // Sem contato, sem o link: o e-mail é um extra do rodapé, e a falha do pedido já aparece na
      // rede do navegador. Um aviso no console repetiria isso a cada carga do terraço.
      () => undefined,
    );
    return () => {
      montado = false;
    };
  }, [obterContato]);

  return (
    <footer className="text-[0.875rem] leading-[1.4]">
      <ul className="m-0 p-0 list-none flex flex-wrap gap-x-(--e-5)">
        <li>
          <a href="/termos" className={LINK}>
            Termos de uso
          </a>
        </li>
        <li>
          <a href="/privacidade" className={LINK}>
            Privacidade
          </a>
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
