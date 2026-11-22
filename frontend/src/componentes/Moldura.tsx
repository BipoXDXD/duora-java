import type { ReactNode } from "react";
import type { Momento } from "../dominio/dados";

interface PropsMoldura {
  momento: Momento;
  /** Liga .confirmando-saida: no celular o topo cresce e deixa de ficar preso (sem :has()). */
  confirmandoSaida: boolean;
  /** Liga .ceu-pintado, que a cena usa quando a imagem do céu carregou. */
  ceuPintado: boolean;
  /** data-tela-cheia: a noite revelada ocupa a tela do celular em pé (revelacao.css); padrão false. */
  telaCheia?: boolean;
  children: ReactNode;
}

/**
 * Cena à esquerda e topo com painel à direita; no celular em pé, topo, cena e painel empilhados.
 * No celular deitado voltam as duas colunas, na altura da tela: empilhada, a cena tomaria a tela inteira.
 */
const CLASSES_DA_MOLDURA = [
  "app group grid grid-cols-[minmax(0,1fr)_var(--largura-painel)] grid-rows-[auto_minmax(0,1fr)]",
  "[grid-template-areas:'cena_topo'_'cena_painel'] h-dvh min-h-[560px]",
  "celular:grid-cols-[minmax(0,1fr)] celular:grid-rows-[auto_auto_auto]",
  "celular:[grid-template-areas:'topo'_'cena'_'painel'] celular:h-auto celular:min-h-dvh",
  "celular-deitado:grid-cols-[minmax(0,55fr)_minmax(0,45fr)] celular-deitado:grid-rows-[auto_minmax(0,1fr)]",
  "celular-deitado:[grid-template-areas:'cena_topo'_'cena_painel'] celular-deitado:h-dvh celular-deitado:min-h-0",
].join(" ");

export function Moldura({ momento, confirmandoSaida, ceuPintado, telaCheia = false, children }: PropsMoldura) {
  const classes = [CLASSES_DA_MOLDURA];
  if (confirmandoSaida) {
    classes.push("confirmando-saida");
  }
  if (ceuPintado) {
    classes.push("ceu-pintado");
  }
  return (
    <div className={classes.join(" ")} data-momento={momento} data-tela-cheia={String(telaCheia)}>
      {children}
    </div>
  );
}
