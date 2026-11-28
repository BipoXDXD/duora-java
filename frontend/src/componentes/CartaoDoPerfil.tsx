import type { ReactElement } from "react";
import type { PerfilPublico } from "../api/perfil";
import { FotoOuSilhueta } from "./FotoOuSilhueta";
import { SeloVerificado } from "./SeloVerificado";

/**
 * O que se vê de alguém: foto (ou silhueta), apelido, pronomes e o selo. Sem pronomes ou sem selo, a
 * linha some, sem deixar espaço vazio. O título da tela fica com quem usa o cartão; se ele já é o
 * apelido, `semApelido` evita repeti-lo para o leitor de tela.
 */
export function CartaoDoPerfil({
  perfil,
  carregamento = "eager",
  semApelido = false,
}: {
  perfil: PerfilPublico;
  /** `eager` quando o cartão é o principal da tela; abaixo da dobra, `lazy`. */
  carregamento?: "lazy" | "eager";
  /** A tela já mostra o apelido no título (h1): o cartão não o repete. */
  semApelido?: boolean;
}): ReactElement {
  return (
    <div className="grid justify-items-start gap-(--e-3)">
      <FotoOuSilhueta
        url={perfil.foto}
        alt={`Foto de ${perfil.apelido}`}
        tamanho="grande"
        carregamento={carregamento}
      />
      <div className="grid gap-(--e-1)">
        {!semApelido && (
          <p className="m-0 font-titulo font-semibold text-[1.375rem] leading-[1.2] [overflow-wrap:anywhere]">
            {perfil.apelido}
          </p>
        )}
        {perfil.pronomes !== null && (
          <p className="m-0 text-texto-2 leading-[1.5] [overflow-wrap:anywhere]">{perfil.pronomes}</p>
        )}
        {perfil.verificado && <SeloVerificado />}
      </div>
    </div>
  );
}
