import { useQuery } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TEXTO_SECUNDARIO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { consultaDasVersoesLegais } from "../../rotas/consultas";
import { ENCARREGADO } from "./textos";

const LINK_EM_TEXTO =
  "text-ambar underline underline-offset-4 [overflow-wrap:anywhere] hover:text-ambar-claro [transition:color_var(--transicao)]";

/**
 * Contato do encarregado de dados (LGPD), sem sessão. O nome e o e-mail vêm das versões legais, o
 * mesmo cache que o rodapé lê. O nome chega como frase ("o responsável pelo Duora"): só a primeira
 * letra aparece maiúscula, sem mudar o texto lido.
 */
export function Encarregado(): ReactElement {
  const versoes = useQuery(consultaDasVersoesLegais);

  return (
    <>
      <title>{ENCARREGADO.tituloDaAba}</title>
      <h1 className={TITULO_DA_CASCA}>{ENCARREGADO.titulo}</h1>
      <p className={`mt-(--e-4) ${TEXTO_SECUNDARIO} text-[1.0625rem] leading-[1.5]`}>{ENCARREGADO.uso}</p>
      {versoes.isSuccess ? (
        <dl className="mt-(--e-6) mb-0 grid gap-(--e-4)">
          <div className="grid gap-(--e-1)">
            <dt className="text-texto-3 text-[0.875rem] font-semibold">{ENCARREGADO.nome}</dt>
            <dd className="m-0 text-[1.125rem] first-letter:uppercase">{versoes.data.encarregado.nome}</dd>
          </div>
          <div className="grid gap-(--e-1)">
            <dt className="text-texto-3 text-[0.875rem] font-semibold">{ENCARREGADO.email}</dt>
            <dd className="m-0 text-[1.125rem]">
              <a href={`mailto:${versoes.data.encarregado.email}`} className={LINK_EM_TEXTO}>
                {versoes.data.encarregado.email}
              </a>
            </dd>
          </div>
        </dl>
      ) : versoes.isError ? (
        <div className="mt-(--e-6) grid gap-(--e-3)">
          <p className={TEXTO_SECUNDARIO}>{ENCARREGADO.falha}</p>
          <button
            type="button"
            className={BOTAO_SECUNDARIO_SEM_MARGEM}
            aria-disabled={versoes.isFetching ? true : undefined}
            onClick={() => {
              if (!versoes.isFetching) {
                void versoes.refetch();
              }
            }}
          >
            {ENCARREGADO.tentarDeNovo}
          </button>
        </div>
      ) : (
        <p className={`mt-(--e-6) ${TEXTO_SECUNDARIO}`} role="status">
          {ENCARREGADO.carregando}
        </p>
      )}
    </>
  );
}
