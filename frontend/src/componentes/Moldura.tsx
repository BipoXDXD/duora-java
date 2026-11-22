import type { ReactNode } from "react";
import type { Momento } from "../dominio/dados";

interface PropsMoldura {
  momento: Momento;
  /** Liga .confirmando-saida: no celular o topo cresce e deixa de ficar preso (sem :has()). */
  confirmandoSaida: boolean;
  /** Liga .ceu-pintado, que a cena usa quando a imagem do céu carregou. */
  ceuPintado: boolean;
  children: ReactNode;
}

/** Cena à esquerda e topo com painel à direita; no celular, topo, cena e painel empilhados. */
const CLASSES_DA_MOLDURA = [
  "app group grid grid-cols-[minmax(0,1fr)_var(--largura-painel)] grid-rows-[auto_minmax(0,1fr)]",
  "[grid-template-areas:'cena_topo'_'cena_painel'] h-dvh min-h-[560px]",
  "celular:grid-cols-[minmax(0,1fr)] celular:grid-rows-[auto_auto_auto]",
  "celular:[grid-template-areas:'topo'_'cena'_'painel'] celular:h-auto celular:min-h-dvh",
].join(" ");

export function Moldura({ momento, confirmandoSaida, ceuPintado, children }: PropsMoldura) {
  const classes = [CLASSES_DA_MOLDURA];
  if (confirmandoSaida) {
    classes.push("confirmando-saida");
  }
  if (ceuPintado) {
    classes.push("ceu-pintado");
  }
  return (
    <div className={classes.join(" ")} data-momento={momento}>
      {children}
    </div>
  );
}
