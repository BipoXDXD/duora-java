import { type ReactElement, useEffect, useState } from "react";
import { type Conta, obterConta } from "../api/estadoDaConta";
import { BOTAO_TEXTO } from "./estilos";

interface PropsLinkDaConta {
  obter?: () => Promise<Conta | null>;
}

/** Mesmo visual do Sair, que ocupa este lugar no convite e na montagem. */
const CLASSES_DO_LINK = `link-da-conta ${BOTAO_TEXTO} inline-flex items-center flex-none`;

/**
 * Link para a casca de conta no topo do terraço: "Entrar" para quem está anônimo, "Minha conta" para
 * quem tem sessão. É um <a> comum (página inteira): o terraço não carrega o roteador da casca. Antes
 * da resposta, ou se ela falhar, não mostra nada; o encontro segue sem o link.
 */
export function LinkDaConta({ obter = obterConta }: PropsLinkDaConta): ReactElement | null {
  // undefined: ainda sem resposta (ou ela falhou); null: anônimo.
  const [conta, setConta] = useState<Conta | null | undefined>(undefined);

  useEffect(() => {
    let montado = true;
    obter().then(
      (lida) => {
        if (montado) {
          setConta(lida);
        }
      },
      () => {
        // A falha só tira o link: o terraço anônimo não depende da conta.
      },
    );
    return () => {
      montado = false;
    };
  }, [obter]);

  if (conta === undefined) {
    return null;
  }
  return conta === null ? (
    <a href="/entrar" className={CLASSES_DO_LINK}>
      Entrar
    </a>
  ) : (
    <a href="/conta" className={CLASSES_DO_LINK}>
      Minha conta
    </a>
  );
}
