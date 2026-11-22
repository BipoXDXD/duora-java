import { useCenario } from "../cenario/contexto";
import type { CenarioFront } from "../cenario/tipos";
import { porArtigo } from "../dominio/artigos";
import { LARGURA_MINIATURA } from "../dominio/dados";
import { textoBloqueio } from "../dominio/mensagens";
import { aberturaAtiva, type Visao, type VisaoPeca, type VisaoSinal } from "../dominio/visao";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { Acao } from "../sessao/tipos";
import { BOTAO_SECUNDARIO, INDISPONIVEL_EM_ALTO_CONTRASTE, SUBTITULO, seDisponivel } from "./estilos";

interface PropsInventario {
  visao: Visao;
  selecionada: string | null;
  enviando: Acao | null;
  acoes: Pick<AcoesDaSessao, "escolherPeca" | "retirar" | "moverPara">;
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

/**
 * O aceite da sugestão, abaixo do item e alinhado ao texto dele. O contorno coral repete o do selo
 * "Sugerido mover" e o do lugar sugerido na cena: os três falam da mesma sugestão.
 */
const CLASSES_DO_MOVER = [
  "botao-mover min-h-[44px] mt-(--e-1) ml-[72px] px-(--e-4) py-(--e-2) border-2 border-coral rounded-(--raio)",
  "[background:none] text-marfim font-semibold cursor-pointer [transition:background_var(--transicao)]",
  "hover:not-aria-disabled:bg-[rgb(240_154_145_/_0.14)] aria-disabled:opacity-70 aria-disabled:cursor-progress",
  INDISPONIVEL_EM_ALTO_CONTRASTE,
].join(" ");

/** A sugestão do parceiro para uma peça sua, se houver. */
function sugestaoRecebida(visao: Visao, peca: string): VisaoSinal | null {
  const { sinal } = visao;
  return sinal !== null && sinal.peca === peca && sinal.deAssento !== visao.voce.assento ? sinal : null;
}

/** O que o item diz embaixo do nome: o bloqueio, onde está, a troca que fará ou para onde vai. */
function estadoDoItem(c: CenarioFront, p: VisaoPeca, minhas: VisaoPeca[], visao: Visao): string {
  const info = c.peca(p.peca);
  if (p.bloqueio) {
    return textoBloqueio(p.bloqueio, visao);
  }
  if (p.lugar) {
    return `No terraço: ${c.lugar(p.lugar).nome}`;
  }
  const outraSecundaria = minhas.find((o) => o !== p && !c.peca(o.peca).obrigatoria && o.lugar);
  if (!info.obrigatoria && outraSecundaria) {
    return `Troca ${c.peca(outraSecundaria.peca).artigo} ${porArtigo(info.artigo)}`;
  }
  return c.zona(info.zona).descricao;
}

interface PropsItem {
  peca: VisaoPeca;
  estado: string;
  pressionado: boolean;
  indisponivel: boolean;
  aoEscolher: (peca: string) => void;
  /** O lugar que o parceiro sugeriu para a peça, ou null. */
  sugerido: string | null;
  /** Um envio em voo: o "Mover …" espera. */
  enviando: boolean;
  aoMoverPara: (peca: string, lugar: string) => void;
}

function Item({ peca, estado, pressionado, indisponivel, aoEscolher, sugerido, enviando, aoMoverPara }: PropsItem) {
  const c = useCenario();
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
          src={c.urlSprite(c.sprite(peca.peca, peca.lugar), "crepusculo", LARGURA_MINIATURA)}
        />
        <span>
          <span className="nome-item block font-semibold">{c.peca(peca.peca).nome}</span>
          <span className="estado-item block text-texto-2 text-[0.875rem] leading-[1.35]">{estado}</span>
        </span>
      </button>
      {sugerido !== null && (
        <button
          type="button"
          className={CLASSES_DO_MOVER}
          aria-disabled={enviando ? true : undefined}
          onClick={seDisponivel(enviando, () => {
            aoMoverPara(peca.peca, sugerido);
          })}
        >
          {`Mover ${c.peca(peca.peca).artigo}: ${c.lugar(sugerido).nome}`}
        </button>
      )}
    </li>
  );
}

/** "Suas peças": as centrais primeiro e, depois de um aviso, as laterais para escolher. Some durante a abertura e na noite revelada. */
export function Inventario({ visao, selecionada, enviando, acoes }: PropsInventario) {
  const c = useCenario();
  const minhas = visao.pecas.filter((p) => p.assento === visao.voce.assento);
  const primeiraLateral = minhas.find((p) => !c.peca(p.peca).obrigatoria);
  const selecionadaColocada = minhas.find((p) => p.peca === selecionada && p.lugar);
  const podeRetirar = selecionadaColocada !== undefined && !c.peca(selecionadaColocada.peca).obrigatoria;

  return (
    <section
      className="inventario celular:order-2 celular-deitado:order-2"
      aria-labelledby="inventario-titulo"
      hidden={aberturaAtiva(visao) || visao.fase === "REVELADO"}
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
              estado={estadoDoItem(c, p, minhas, visao)}
              pressionado={selecionada === p.peca}
              indisponivel={Boolean(p.bloqueio) || enviando !== null}
              aoEscolher={(peca) => void acoes.escolherPeca(peca, true)}
              sugerido={sugestaoRecebida(visao, p.peca)?.lugar ?? null}
              enviando={enviando !== null}
              aoMoverPara={(peca, lugar) => void acoes.moverPara(peca, lugar)}
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
        onClick={seDisponivel(enviando !== null, () => void acoes.retirar())}
      >
        {enviando === "retirar" ? "Enviando…" : "Tirar do terraço"}
      </button>
    </section>
  );
}
