import { useQuery } from "@tanstack/react-query";
import type { ReactElement } from "react";
import { ErroHttp } from "../../api/http";
import { type DocumentoLegal, obterDocumentoLegal } from "../../api/legal";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TEXTO_SECUNDARIO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { NaoEncontrada } from "../../rotas/NaoEncontrada";

const TITULOS: Record<DocumentoLegal, string> = {
  termos: "Termos de uso",
  privacidade: "Política de privacidade",
};

const LEGAL = {
  carregando: "Carregando o documento…",
  falha: "Não conseguimos carregar o documento. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
  versaoDe: "Versão de",
} as const;

/**
 * O HTML gerado pelo servidor, com tipografia de leitura. Sem plugin de tipografia: cada elemento
 * que o Markdown dos documentos produz (títulos, parágrafos, listas, links, tabelas) tem o seu estilo.
 */
const PROSA = [
  "mt-(--e-6) text-texto-2 text-[1.0625rem] leading-[1.6]",
  "[&_h2]:mt-(--e-7) [&_h2]:mb-(--e-3) [&_h2]:font-titulo [&_h2]:font-semibold [&_h2]:text-marfim [&_h2]:text-[1.375rem] [&_h2]:leading-[1.25]",
  "[&_h3]:mt-(--e-6) [&_h3]:mb-(--e-2) [&_h3]:font-semibold [&_h3]:text-marfim [&_h3]:text-[1.125rem]",
  "[&_p]:my-(--e-3) [&_ul]:my-(--e-3) [&_ul]:pl-(--e-5) [&_ul]:list-disc [&_ol]:my-(--e-3) [&_ol]:pl-(--e-5) [&_ol]:list-decimal [&_li]:my-(--e-2)",
  "[&_strong]:font-semibold [&_strong]:text-marfim",
  "[&_a]:text-ambar [&_a]:underline [&_a]:underline-offset-4 [&_a]:[overflow-wrap:anywhere] [&_a:hover]:text-ambar-claro",
  "[&_table]:my-(--e-4) [&_table]:w-full [&_table]:border-collapse [&_table]:text-[0.9375rem] [&_table]:leading-[1.45]",
  "[&_th]:py-(--e-2) [&_th]:pr-(--e-4) [&_th]:text-left [&_th]:align-bottom [&_th]:font-semibold [&_th]:text-marfim [&_th]:border-b [&_th]:border-ceu-600",
  "[&_td]:py-(--e-2) [&_td]:pr-(--e-4) [&_td]:align-top [&_td]:border-b [&_td]:border-noite-700",
].join(" ");

/** "2026-11-22" vira "22/11/2026", sem passar por Date (nenhum fuso desloca o dia). */
function dataDaVersao(versao: string): string {
  return versao.replace(/^(\d{4})-(\d{2})-(\d{2})$/, "$3/$2/$1");
}

/** O Markdown abre com o título do documento, que a página já mostra junto da versão. */
function semOTituloInicial(html: string): string {
  return html.replace(/^\s*<h1>[^<]*<\/h1>\s*/, "");
}

/**
 * Termos de uso ou Política de privacidade, sem sessão. O título fica fixo enquanto o texto carrega;
 * a versão e o texto chegam juntos. Um documento que o servidor não conhece cai na página não
 * encontrada; outra falha deixa tentar de novo.
 */
export function DocumentoLegalPagina({ documento }: { documento: DocumentoLegal }): ReactElement {
  const consulta = useQuery({ queryKey: ["legal", documento], queryFn: () => obterDocumentoLegal(documento) });
  const titulo = TITULOS[documento];

  if (consulta.error instanceof ErroHttp && consulta.error.codigo === "DOCUMENTO_INEXISTENTE") {
    return <NaoEncontrada />;
  }
  return (
    <article>
      <title>{`${titulo} · Duora`}</title>
      <h1 className={TITULO_DA_CASCA}>{titulo}</h1>
      {consulta.isSuccess ? (
        <>
          <p className="mt-(--e-3) mb-0 text-texto-3 text-[0.9375rem]">
            <time dateTime={consulta.data.versao}>{`${LEGAL.versaoDe} ${dataDaVersao(consulta.data.versao)}`}</time>
          </p>
          <div
            className={PROSA}
            // Só HTML de /api/legal/*: o servidor gera do Markdown versionado no repositório, com HTML
            // bruto escapado e links saneados. Nada aqui vem de quem usa o Duora.
            dangerouslySetInnerHTML={{ __html: semOTituloInicial(consulta.data.html) }}
          />
        </>
      ) : consulta.isError ? (
        <div className="mt-(--e-6) grid gap-(--e-3)">
          <p className={TEXTO_SECUNDARIO}>{LEGAL.falha}</p>
          <button
            type="button"
            className={BOTAO_SECUNDARIO_SEM_MARGEM}
            aria-disabled={consulta.isFetching ? true : undefined}
            onClick={() => {
              if (!consulta.isFetching) {
                void consulta.refetch();
              }
            }}
          >
            {LEGAL.tentarDeNovo}
          </button>
        </div>
      ) : (
        <p className={`mt-(--e-6) ${TEXTO_SECUNDARIO}`} role="status">
          {LEGAL.carregando}
        </p>
      )}
    </article>
  );
}

export function TermosDeUso(): ReactElement {
  return <DocumentoLegalPagina documento="termos" />;
}

export function PoliticaDePrivacidade(): ReactElement {
  return <DocumentoLegalPagina documento="privacidade" />;
}
