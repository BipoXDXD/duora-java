import { LARGURA_MINIATURA, dadosDaPeca, dadosDoLugar, sprite, urlSprite, type DadosPeca } from "../dominio/dados";
import { textoBloqueio } from "../dominio/mensagens";
import type { Visao, VisaoPeca } from "../dominio/visao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, INDISPONIVEL_EM_ALTO_CONTRASTE, SUBTITULO, seDisponivel } from "./estilos";

interface PropsInventario {
  visao: Visao;
  selecionada: string | null;
  enviando: Acao | null;
  aoEscolher: (peca: string) => void;
  aoRetirar: () => void;
}

/** A miniatura esmaece no item indisponível; o texto do motivo mantém o contraste. */
const CLASSES_DO_ITEM = [
  "item group/item grid grid-cols-[56px_1fr] items-center gap-(--e-3) w-full min-h-[64px] pl-(--e-1) pr-(--e-3)",
  "py-(--e-1) border border-transparent rounded-(--raio) bg-noite-700 text-left cursor-pointer",
  "[transition:background_var(--transicao),border-color_var(--transicao)] hover:not-aria-pressed:bg-[#22465f]",
  "aria-pressed:border-ambar aria-pressed:bg-[#2a4a5e] aria-disabled:cursor-not-allowed",
  "forced-colors:aria-pressed:border-[3px] forced-colors:aria-pressed:border-double",
  "forced-colors:aria-pressed:not-aria-disabled:border-[color:Highlight]",
  INDISPONIVEL_EM_ALTO_CONTRASTE,
].join(" ");

function descreverZona(info: DadosPeca): string {
  return info.zona === "centro" ? "Vai na área de observação, no centro" : "Vai em um dos lugares laterais";
}

/** O que o item diz embaixo do nome: o bloqueio, onde está, a troca que fará ou para onde vai. */
function estadoDoItem(p: VisaoPeca, minhas: VisaoPeca[], visao: Visao): string {
  const info = dadosDaPeca(p.peca);
  if (p.bloqueio) {
    return textoBloqueio(p.bloqueio, visao);
  }
  if (p.lugar) {
    return `No terraço: ${dadosDoLugar(p.lugar).nome}`;
  }
  const outraSecundaria = minhas.find((o) => o !== p && !dadosDaPeca(o.peca).obrigatoria && o.lugar);
  if (!info.obrigatoria && outraSecundaria) {
    return `Troca ${dadosDaPeca(outraSecundaria.peca).artigo} por ${info.artigo}`;
  }
  return descreverZona(info);
}

interface PropsItem {
  peca: VisaoPeca;
  estado: string;
  pressionado: boolean;
  indisponivel: boolean;
  aoEscolher: (peca: string) => void;
}

function Item({ peca, estado, pressionado, indisponivel, aoEscolher }: PropsItem) {
  return (
    <li>
      <button
        type="button"
        className={CLASSES_DO_ITEM}
        data-peca={peca.peca}
        aria-pressed={pressionado}
        aria-disabled={indisponivel ? true : undefined}
        onClick={seDisponivel(indisponivel, () => {
          aoEscolher(peca.peca);
        })}
      >
        <img
          className="miniatura w-[56px] h-[56px] object-contain group-aria-disabled/item:opacity-50"
          alt=""
          src={urlSprite(sprite(peca.peca, peca.lugar ?? "C2"), "crepusculo", LARGURA_MINIATURA)}
        />
        <span>
          <span className="nome-item block font-semibold">{dadosDaPeca(peca.peca).nome}</span>
          <span className="estado-item block text-texto-2 text-[0.875rem] leading-[1.35]">{estado}</span>
        </span>
      </button>
    </li>
  );
}

/** "Suas peças": as centrais primeiro e, depois de um aviso, as laterais para escolher. Some na noite revelada. */
export function Inventario({ visao, selecionada, enviando, aoEscolher, aoRetirar }: PropsInventario) {
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento);
  const primeiraLateral = minhas.find((p) => !dadosDaPeca(p.peca).obrigatoria);
  const selecionadaColocada = minhas.find((p) => p.peca === selecionada && p.lugar);
  const podeRetirar = selecionadaColocada !== undefined && !dadosDaPeca(selecionadaColocada.peca).obrigatoria;

  return (
    <section
      className="inventario celular:order-2"
      aria-labelledby="inventario-titulo"
      hidden={visao.fase === "REVELADO"}
    >
      <h2 id="inventario-titulo" className={SUBTITULO}>
        Suas peças
      </h2>
      <ul id="inventario" className="grid gap-(--e-2) m-0 p-0 list-none">
        {minhas.flatMap((p) => {
          const item = (
            <Item
              key={p.peca}
              peca={p}
              estado={estadoDoItem(p, minhas, visao)}
              pressionado={selecionada === p.peca}
              indisponivel={Boolean(p.bloqueio) || enviando !== null}
              aoEscolher={aoEscolher}
            />
          );
          if (p !== primeiraLateral) {
            return [item];
          }
          return [
            <li key="grupo-escolha" className="grupo-escolha mt-(--e-2) mx-0 mb-0 text-texto-3 text-[0.875rem]">
              Escolha uma destas para as laterais:
            </li>,
            item,
          ];
        })}
      </ul>
      <button
        type="button"
        className={BOTAO_SECUNDARIO}
        id="retirar"
        hidden={!podeRetirar}
        aria-disabled={enviando !== null ? true : undefined}
        onClick={seDisponivel(enviando !== null, aoRetirar)}
      >
        {enviando === "retirar" ? "Enviando…" : "Tirar do terraço"}
      </button>
    </section>
  );
}
