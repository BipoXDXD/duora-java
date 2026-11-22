import { dadosDaPeca } from "../dominio/dados";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { SUBTITULO } from "./estilos";

const ehObrigatoria = (p: VisaoPeca) => dadosDaPeca(p.peca).obrigatoria === true;
const temSegundaColocada = (lista: VisaoPeca[]) => lista.some((p) => !ehObrigatoria(p) && p.lugar !== null);

/** As quatro metas do terraço, com o que cada uma diz e se já foi cumprida. */
function linhasDaFalta(visao: Visao): [texto: string, feito: boolean][] {
  const nomeParceiro = visao.parceiro?.nome ?? "a outra pessoa";
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento);
  const dele = visao.pecas.filter((p) => p.assento !== visao.voce.assento);
  const minhaCentral = minhas.find(ehObrigatoria);
  if (minhaCentral === undefined) {
    throw new Error("Visão sem a peça central de quem pede");
  }
  const centralDele = dele.find(ehObrigatoria);
  return [
    [`${dadosDaPeca(minhaCentral.peca).nome} no centro`, minhaCentral.lugar !== null],
    [
      `${centralDele ? dadosDaPeca(centralDele.peca).nome : "Peça central"} no centro (${nomeParceiro})`,
      Boolean(centralDele?.lugar),
    ],
    ["Sua segunda peça", temSegundaColocada(minhas)],
    [`Segunda peça de ${nomeParceiro}`, temSegundaColocada(dele)],
  ];
}

function IconeFeito() {
  return (
    <svg className="flex-none w-[18px] h-[18px] text-verde-agua" viewBox="0 0 20 20" aria-hidden="true">
      <circle cx="10" cy="10" r="9" fill="currentColor" opacity=".25" />
      <path
        d="M5.5 10.5l3 3 6-7"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function IconePendente() {
  return (
    <svg className="flex-none w-[18px] h-[18px]" viewBox="0 0 20 20" aria-hidden="true">
      <circle cx="10" cy="10" r="8" fill="none" stroke="currentColor" strokeWidth="1.5" strokeDasharray="3 3" />
    </svg>
  );
}

/** "O que falta": some quando a noite é revelada. */
export function Falta({ visao }: { visao: Visao }) {
  return (
    <section className="falta celular:order-4" aria-labelledby="falta-titulo" hidden={visao.fase === "REVELADO"}>
      <h2 id="falta-titulo" className={SUBTITULO}>
        O que falta
      </h2>
      <ul id="falta" className="grid gap-(--e-1) m-0 p-0 list-none text-texto-2">
        {linhasDaFalta(visao).map(([texto, feito], posicao) => (
          // As quatro linhas são fixas e posicionais: a posição é a identidade.
          <li key={posicao} className="flex items-center gap-(--e-2) data-[feito=true]:text-texto-3" data-feito={feito}>
            {feito ? <IconeFeito /> : <IconePendente />}
            {texto}
            <span className="so-leitor">{feito ? ", pronto" : ", falta"}</span>
          </li>
        ))}
      </ul>
    </section>
  );
}
