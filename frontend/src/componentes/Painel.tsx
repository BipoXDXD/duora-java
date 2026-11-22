import type { ReactNode, Ref } from "react";
import type { Visao } from "../dominio/visao";
import { naoAlcancavel } from "../sessao/naoAlcancavel";
import type { EstadoDaSessao } from "../sessao/tipos";
import { Acoes } from "./Acoes";
import { Convite } from "./Convite";
import { Encerrado } from "./Encerrado";
import { Entrada } from "./Entrada";
import { Falta } from "./Falta";
import { Inventario } from "./Inventario";
import { Par } from "./Par";
import { Resultado } from "./Resultado";

export interface PropsPainel {
  estado: EstadoDaSessao;
  /** O #aviso fica vazio no JSX: quem escreve nele é o criarAviso, por esta ref (uma mutação por aviso). */
  refAviso: Ref<HTMLParagraphElement>;
  aoEnviarApelido: (apelido: string) => void;
  /** Depois de cada tentativa de copiar o convite, com o aviso a anunciar. */
  aoCopiarConvite: (aviso: string) => void;
  aoCriarOutro: () => void;
  aoEscolherPeca: (peca: string) => void;
  aoRetirar: () => void;
  aoConfirmar: () => void;
  aoAjustar: () => void;
  aoAlternarSom: () => void;
}

/** O que muda de uma tela para outra: o par (quando há sala), a instrução (menos no encerrado) e o bloco da tela. */
interface TelaDoPainel {
  visaoDoPar: Visao | null;
  instrucao: string | null;
  conteudo: ReactNode;
}

function linkDoConvite(token: string | null): string {
  return `${window.location.origin}/#convite=${token ?? ""}`;
}

function telaDaVez(props: Omit<PropsPainel, "refAviso">): TelaDoPainel {
  const { estado } = props;
  switch (estado.tela) {
    case "carregando":
      return { visaoDoPar: null, instrucao: "Procurando seu terraço…", conteudo: null };
    case "entrada":
      return {
        visaoDoPar: null,
        instrucao: estado.comConvite
          ? "Você foi chamado para montar um terraço a dois. Diga como quer ser chamado."
          : "Monte um terraço a dois antes de a noite cair. Comece pelo seu apelido.",
        conteudo: (
          <Entrada
            comConvite={estado.comConvite}
            enviando={estado.enviando === "entrada"}
            erro={estado.erroApelido}
            aoEnviar={props.aoEnviarApelido}
          />
        ),
      };
    case "convite":
      return {
        visaoDoPar: estado.visao,
        instrucao:
          "Mande o convite para a outra pessoa. O terraço abre sozinho quando ela entrar; deixe esta janela aberta.",
        conteudo: <Convite link={linkDoConvite(estado.visao.convite)} aoCopiar={props.aoCopiarConvite} />,
      };
    case "montagem":
      return {
        visaoDoPar: estado.visao,
        instrucao: estado.instrucao,
        conteudo: (
          // Agrupa as seções sem mudar o layout: no celular elas ainda são reordenadas no painel.
          <div className="montagem contents" id="montagem">
            <Falta visao={estado.visao} />
            <Inventario
              visao={estado.visao}
              selecionada={estado.selecionada}
              enviando={estado.enviando}
              aoEscolher={props.aoEscolherPeca}
              aoRetirar={props.aoRetirar}
            />
            <Resultado visao={estado.visao} enviando={estado.enviando} aoAjustar={props.aoAjustar} />
            <Acoes
              visao={estado.visao}
              enviando={estado.enviando}
              somLigado={estado.somLigado}
              aoConfirmar={props.aoConfirmar}
              aoAlternarSom={props.aoAlternarSom}
            />
          </div>
        ),
      };
    case "encerrado":
      return {
        visaoDoPar: null,
        instrucao: null,
        conteudo: <Encerrado texto={estado.texto} aoCriarOutro={props.aoCriarOutro} />,
      };
    default:
      return naoAlcancavel(estado);
  }
}

/** No celular a ordem muda: aviso, instrução, a tela (ou as peças), o resultado, o que falta e as ações. */
export function Painel({ refAviso, ...props }: PropsPainel) {
  const { visaoDoPar, instrucao, conteudo } = telaDaVez(props);
  return (
    <main
      className="painel [grid-area:painel] flex flex-col gap-(--e-5) px-(--e-6) pt-(--e-5) pb-(--e-6) bg-noite-800 overflow-y-auto celular:px-(--e-4) celular:overflow-visible"
      aria-label="Painel do terraço"
    >
      {visaoDoPar && <Par visao={visaoDoPar} />}
      {instrucao !== null && (
        <p
          className="instrucao m-0 text-[1.25rem] leading-[1.35] max-w-[32ch] celular:order-1 celular:text-[1.125rem]"
          id="instrucao"
        >
          {instrucao}
        </p>
      )}
      {conteudo}
      <p
        ref={refAviso}
        className="aviso m-0 min-h-[1.5em] text-verde-agua data-[tipo=erro]:text-coral celular:order-0"
        id="aviso"
        role="status"
        aria-live="polite"
      ></p>
    </main>
  );
}
