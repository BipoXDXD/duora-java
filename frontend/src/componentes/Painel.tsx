import type { ReactNode, Ref } from "react";
import type { Visao } from "../dominio/visao";
import { naoAlcancavel } from "../sessao/naoAlcancavel";
import type { AcoesDaSessao } from "../sessao/sessao";
import type { EstadoDaSessao } from "../sessao/tipos";
import type { TelaCheia } from "../useTelaCheia";
import { Acoes, BotaoSom } from "./Acoes";
import { Convite } from "./Convite";
import { Encerrado } from "./Encerrado";
import { Entrada } from "./Entrada";
import { CLASSES_DO_PAINEL } from "./estilos";
import { Falta } from "./Falta";
import { Inventario } from "./Inventario";
import { Par } from "./Par";
import { Resultado } from "./Resultado";

export interface PropsPainel {
  estado: EstadoDaSessao;
  /** O #aviso fica vazio no JSX: quem escreve nele é o criarAviso, por esta ref (uma mutação por aviso). */
  refAviso: Ref<HTMLParagraphElement>;
  acoes: AcoesDaSessao;
  /** Depois de cada tentativa de copiar o convite, com o aviso a anunciar. */
  aoCopiarConvite: (aviso: string) => void;
  /** Alterna o som na sessão e no áudio: quem toca o som é o App. */
  aoAlternarSom: () => void;
  /** Tela cheia da noite no celular em pé; sem ela (padrão), o resultado fica no painel. */
  telaCheia?: TelaCheia;
}

const SEM_TELA_CHEIA: TelaCheia = { telaCheia: false, verPainel: () => {}, verCeu: () => {} };

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
  const { estado, acoes, telaCheia = SEM_TELA_CHEIA } = props;
  switch (estado.tela) {
    case "carregando":
      return { visaoDoPar: null, instrucao: "Procurando seu terraço…", conteudo: null };
    case "entrada":
      return {
        visaoDoPar: null,
        instrucao: estado.comConvite
          ? "Você recebeu um convite para montar um terraço a dois. Escolha seu apelido."
          : "Monte um terraço a dois antes de a noite cair. Comece pelo seu apelido.",
        conteudo: (
          <Entrada
            comConvite={estado.comConvite}
            troca={estado.trocaDeSala ? { aoContinuar: acoes.continuarNoTerraco } : undefined}
            apelido={estado.apelido}
            // Qualquer ação em voo, também a da sala por trás da troca: o clique seria ignorado.
            enviando={estado.enviando !== null}
            erro={estado.erroApelido}
            aoEnviar={(apelido) => void acoes.enviarApelido(apelido)}
            aoCorrigir={acoes.limparErroApelido}
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
              acoes={acoes}
            />
            <Resultado
              visao={estado.visao}
              enviando={estado.enviando}
              acoes={acoes}
              telaCheia={telaCheia.telaCheia}
              aoVerPainel={telaCheia.verPainel}
              aoVerCeu={telaCheia.verCeu}
            />
            <Acoes
              visao={estado.visao}
              enviando={estado.enviando}
              somLigado={estado.somLigado}
              acoes={acoes}
              aoAlternarSom={props.aoAlternarSom}
            />
          </div>
        ),
      };
    case "encerrado":
      return {
        visaoDoPar: null,
        instrucao: null,
        conteudo: <Encerrado titulo={estado.titulo} texto={estado.texto} acoes={acoes} />,
      };
    default:
      return naoAlcancavel(estado);
  }
}

/** No celular a ordem muda (deitado, o painel rola sozinho, ao lado da cena): par e aviso, instrução, a tela (ou as peças), o resultado, o que falta e as ações. */
export function Painel({ refAviso, ...props }: PropsPainel) {
  const { visaoDoPar, instrucao, conteudo } = telaDaVez(props);
  return (
    <main
      className={`${CLASSES_DO_PAINEL} overflow-y-auto celular:overflow-visible celular-deitado:overflow-y-auto`}
      aria-label="Painel do terraço"
    >
      {/* O status logo abaixo dos nomes: o que o outro fez aparece onde se olha para saber dele. */}
      <div className="par-e-aviso grid gap-(--e-2) celular:order-0 celular-deitado:order-0">
        {visaoDoPar && <Par visao={visaoDoPar} />}
        <p
          ref={refAviso}
          className="aviso m-0 min-h-[1.5em] text-verde-agua data-[tipo=erro]:text-coral"
          id="aviso"
          role="status"
          aria-live="polite"
        ></p>
      </div>
      {instrucao !== null && (
        <p
          className="instrucao m-0 text-[1.25rem] leading-[1.35] max-w-[32ch] celular:order-1 celular:text-[1.125rem] celular-deitado:order-1 celular-deitado:text-[1.125rem]"
          id="instrucao"
        >
          {instrucao}
        </p>
      )}
      {conteudo}
      {props.estado.tela !== "montagem" && (
        <BotaoSom somLigado={props.estado.somLigado} aoAlternarSom={props.aoAlternarSom} oculto />
      )}
    </main>
  );
}
