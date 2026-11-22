import { useCenario } from "../cenario/contexto";
import { interpolar } from "../cenario/legenda";
import type { CenarioFront } from "../cenario/tipos";
import type { Visao, VisaoPeca } from "../dominio/visao";
import { SUBTITULO } from "./estilos";

/** As quatro metas da composição, com o que cada uma diz e se já foi cumprida. */
export function linhasDaFalta(c: CenarioFront, visao: Visao): [texto: string, feito: boolean][] {
  const ehObrigatoria = (p: VisaoPeca) => c.peca(p.peca).obrigatoria;
  const temSegundaColocada = (lista: VisaoPeca[]) => lista.some((p) => !ehObrigatoria(p) && p.lugar !== null);
  const nomeParceiro = visao.parceiro?.nome ?? "a outra pessoa";
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento);
  const dele = visao.pecas.filter((p) => p.assento !== visao.voce.assento);
  const minhaCentral = minhas.find(ehObrigatoria);
  if (minhaCentral === undefined) {
    throw new Error("Visão sem a peça central de quem pede");
  }
  const centralDele = dele.find(ehObrigatoria);
  const naZona = (p: VisaoPeca, nome = c.peca(p.peca).nome) =>
    interpolar(c.zona(c.peca(p.peca).zona).checklist, { nome }, c);
  return [
    [naZona(minhaCentral), minhaCentral.lugar !== null],
    [
      // Antes de o parceiro aparecer, a peça central dele vai na mesma zona da minha.
      `${centralDele ? naZona(centralDele) : naZona(minhaCentral, "Peça central")} (${nomeParceiro})`,
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

/** "O que falta": some quando nada falta e quando a noite é revelada. */
export function Falta({ visao }: { visao: Visao }) {
  const c = useCenario();
  const linhas = linhasDaFalta(c, visao);
  const nadaFalta = linhas.every(([, feito]) => feito);
  return (
    <section
      className="falta celular:order-4 celular-deitado:order-4"
      aria-labelledby="falta-titulo"
      hidden={nadaFalta || visao.fase === "REVELADO"}
    >
      <h2 id="falta-titulo" className={SUBTITULO}>
        O que falta
      </h2>
      <ul id="falta" className="grid gap-(--e-1) m-0 p-0 list-none text-texto-2">
        {linhas.map(([texto, feito], posicao) => (
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
