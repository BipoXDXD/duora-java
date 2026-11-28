import { useMutation } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, type ReactElement } from "react";
import { useLoaderData } from "react-router";
import type { Conta } from "../../api/estadoDaConta";
import { ErroHttp } from "../../api/http";
import type { VersoesLegais } from "../../api/legal";
import { iniciarSessao, type Finalidade, type SituacaoDaVerificacao } from "../../api/verificacao";
import { BOTAO_SECUNDARIO_SEM_MARGEM, LINK_DE_ACAO, TITULO_DA_CASCA } from "../../componentes/estilos";
import { CASCA, textoDeEspera } from "../conta/textos";
import { checarCamera, navegadorTemSuporte, type MotivoSemCamera, type ResultadoDaCamera } from "./camera";
import { CapturaDeVida } from "./CapturaDeVida";
import { etapaInicial, proximaEtapa, type Etapa, type Evento } from "./etapas";
import { Explicacao } from "./Explicacao";
import { VERIFICACAO_DE_IDADE } from "./textos";

/** O que o loader da rota entrega: a conta aguardando verificação, a situação dela e as versões legais. */
export interface DadosDaVerificacao {
  conta: Conta;
  situacao: SituacaoDaVerificacao;
  legal: VersoesLegais;
}

interface PropsDaVerificacao {
  /** Saída com recarga completa: a verificação vive num documento próprio. */
  navegarParaPagina?: (url: string) => void;
  checarCamera?: () => Promise<ResultadoDaCamera>;
  temSuporte?: () => boolean;
}

/** Recusas que dizem que esta página não vale mais para a conta: Minha conta leva à tela certa. */
const CODIGOS_DE_RECARGA: readonly string[] = ["VERIFICACAO_FORA_DE_ORDEM", "NAO_AUTENTICADO", "CONTA_INDISPONIVEL"];

const CAMINHO_DA_CONTA = "/conta";

/** O título recebe o foco a cada etapa; o anel só aparece quando o foco veio do teclado. */
const TITULO = [
  TITULO_DA_CASCA,
  "focus:not-focus-visible:[outline:none]",
  "focus-visible:[outline:2px_solid_transparent] focus-visible:outline-offset-2 focus-visible:[box-shadow:var(--foco)]",
].join(" ");

const AVISO = "m-0 text-marfim text-[1.0625rem] leading-[1.5]";
const ESPERA = "m-0 text-texto-2 text-[1.0625rem] leading-[1.5]";

const TEXTO_SEM_CAMERA = {
  negada: VERIFICACAO_DE_IDADE.cameraNegada,
  ausente: VERIFICACAO_DE_IDADE.cameraAusente,
  ocupada: VERIFICACAO_DE_IDADE.cameraOcupada,
} as const satisfies Record<MotivoSemCamera, string>;

function irPara(url: string): void {
  location.assign(url);
}

/**
 * A verificação de idade, no documento próprio da /verificacao. O loader já leu a conta, a situação e
 * as versões legais: a espera da primeira carga é a da casca, e a falha de qualquer leitura vai para o
 * erro da casca. A situação APROVADA volta para Minha conta.
 */
export function VerificacaoPagina({
  navegarParaPagina = irPara,
  checarCamera: checar = checarCamera,
  temSuporte = navegadorTemSuporte,
}: PropsDaVerificacao): ReactElement {
  const { situacao, legal } = useLoaderData<DadosDaVerificacao>();
  const inicial = etapaInicial(situacao);
  return (
    <>
      <title>{VERIFICACAO_DE_IDADE.tituloDaAba}</title>
      {inicial === null ? (
        <VoltaParaAConta navegarParaPagina={navegarParaPagina} />
      ) : (
        <Etapas
          inicial={inicial}
          situacao={situacao}
          emailDoEncarregado={legal.encarregado.email}
          navegarParaPagina={navegarParaPagina}
          checar={checar}
          temSuporte={temSuporte}
        />
      )}
    </>
  );
}

function VoltaParaAConta({ navegarParaPagina }: { navegarParaPagina: (url: string) => void }): ReactElement {
  useEffect(() => {
    navegarParaPagina(CAMINHO_DA_CONTA);
  }, [navegarParaPagina]);
  return (
    <p role="status" className={ESPERA}>
      {CASCA.carregando}
    </p>
  );
}

interface PropsDasEtapas {
  inicial: Etapa;
  situacao: SituacaoDaVerificacao;
  emailDoEncarregado: string;
  navegarParaPagina: (url: string) => void;
  checar: () => Promise<ResultadoDaCamera>;
  temSuporte: () => boolean;
}

/**
 * Cada etapa tem a sua ação de entrada: a checagem da câmera roda em `checandoCamera` e o POST da
 * sessão em `iniciando`. A sessão (com a credencial curta) só entra no redutor: a mutação devolve
 * nada, e o cache de mutações nunca a vê.
 */
function Etapas({
  inicial,
  situacao,
  emailDoEncarregado,
  navegarParaPagina,
  checar,
  temSuporte,
}: PropsDasEtapas): ReactElement {
  const [etapa, despachar] = useReducer(proximaEtapa, inicial);
  const titulo = useRef<HTMLHeadingElement>(null);
  const primeiraEtapa = useRef(true);
  // Trava síncrona do POST da sessão: abre só quando a etapa sai de `iniciando`.
  const iniciando = useRef(false);

  const sessao = useMutation({
    mutationFn: async (finalidade: Finalidade) => {
      despachar({ tipo: "sessaoCriada", sessao: await iniciarSessao(finalidade) });
    },
    gcTime: 0,
    onError: (erro) => {
      if (erro instanceof ErroHttp && CODIGOS_DE_RECARGA.includes(erro.codigo)) {
        navegarParaPagina(CAMINHO_DA_CONTA);
        return;
      }
      despachar({ tipo: "sessaoRecusada", erro });
    },
  });
  const abrirSessao = sessao.mutate;

  useEffect(() => {
    if (primeiraEtapa.current) {
      primeiraEtapa.current = false;
      return;
    }
    titulo.current?.focus();
  }, [etapa.etapa]);

  useEffect(() => {
    if (etapa.etapa !== "iniciando") {
      iniciando.current = false;
    }
    if (etapa.etapa === "checandoCamera") {
      let valendo = true;
      // Uma falha que a checagem não mapeou conta como câmera negada: o texto pede para liberar e tentar de novo.
      void checar()
        .catch((): ResultadoDaCamera => "negada")
        .then((resultado) => {
          if (valendo) {
            despachar({ tipo: "cameraChecada", resultado });
          }
        });
      return () => {
        valendo = false;
      };
    }
    if (etapa.etapa === "iniciando" && !iniciando.current) {
      iniciando.current = true;
      abrirSessao(etapa.finalidade);
    }
    return undefined;
  }, [etapa, checar, abrirSessao]);

  const espera = textoDaEspera(etapa);
  const ocupado = etapa.etapa === "checandoCamera" || etapa.etapa === "iniciando";

  return (
    <>
      <h1 ref={titulo} tabIndex={-1} className={TITULO}>
        {VERIFICACAO_DE_IDADE.titulo}
      </h1>
      {/* A região viva fica sempre montada e fora do trecho ocupado, para o leitor de tela anunciar a espera. */}
      <p role="status" className={espera === null ? "m-0" : `mt-(--e-5) ${ESPERA}`}>
        {espera}
      </p>
      <div aria-busy={ocupado ? true : undefined}>
        <ConteudoDaEtapa
          etapa={etapa}
          situacao={situacao}
          emailDoEncarregado={emailDoEncarregado}
          temSuporte={temSuporte}
          despachar={despachar}
        />
      </div>
    </>
  );
}

interface PropsDoConteudo {
  etapa: Etapa;
  situacao: SituacaoDaVerificacao;
  emailDoEncarregado: string;
  temSuporte: () => boolean;
  despachar: (evento: Evento) => void;
}

/** O texto das etapas de espera; na captura, quem fala é a espera do próprio CapturaDeVida. */
function textoDaEspera(etapa: Etapa): string | null {
  switch (etapa.etapa) {
    case "checandoCamera":
      return VERIFICACAO_DE_IDADE.abrindoCamera;
    case "iniciando":
      return VERIFICACAO_DE_IDADE.preparando;
    case "captura":
    case "explicacao":
    case "semCamera":
    case "conferindo":
    case "falhaDoDetector":
    case "desfecho":
    case "documento":
    case "enviandoDocumento":
    case "limite":
    case "indisponivel":
      return null;
  }
}

function FalhaComRepeticao({ texto, aoRepetir }: { texto: string; aoRepetir: () => void }): ReactElement {
  return (
    <div className="mt-(--e-5) grid gap-(--e-4)">
      <p role="alert" className={AVISO}>
        {texto}
      </p>
      <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={aoRepetir}>
        {VERIFICACAO_DE_IDADE.tentarDeNovo}
      </button>
    </div>
  );
}

function ConteudoDaEtapa({
  etapa,
  situacao,
  emailDoEncarregado,
  temSuporte,
  despachar,
}: PropsDoConteudo): ReactElement | null {
  const tentarDeNovo = () => {
    despachar({ tipo: "tentarDeNovo" });
  };
  switch (etapa.etapa) {
    case "explicacao":
      return (
        <Explicacao
          tentativas={situacao.tentativasRestantesHoje}
          emailDoEncarregado={emailDoEncarregado}
          // Sem a AWS, o detector falso não usa câmera nem wasm.
          temSuporte={situacao.modo === "falso" || temSuporte()}
          aoComecar={() => {
            despachar({ tipo: "comecar", modo: situacao.modo });
          }}
        />
      );
    case "semCamera":
      return <FalhaComRepeticao texto={TEXTO_SEM_CAMERA[etapa.motivo]} aoRepetir={tentarDeNovo} />;
    case "limite":
      return (
        <div className="mt-(--e-5) grid gap-(--e-5)">
          <p className={AVISO}>
            {etapa.segundos === null ? VERIFICACAO_DE_IDADE.limiteDoDia : textoDeEspera(etapa.segundos)}
          </p>
          {/* Página inteira: o terraço não é rota da casca. */}
          <a href="/" className={`justify-self-start ${LINK_DE_ACAO}`}>
            {VERIFICACAO_DE_IDADE.voltarAoTerraco}
          </a>
        </div>
      );
    case "indisponivel":
      return <FalhaComRepeticao texto={VERIFICACAO_DE_IDADE.servicoFora} aoRepetir={tentarDeNovo} />;
    case "captura":
      // O detector vem do modo que o servidor mandou na sessão (D-F2).
      return (
        <CapturaDeVida
          sessao={etapa.sessao}
          aoConcluir={() => {
            despachar({ tipo: "concluiu" });
          }}
          aoFalhar={(falha) => {
            despachar({ tipo: "falhou", falha });
          }}
          aoCancelar={() => {
            despachar({ tipo: "cancelou" });
          }}
        />
      );
    // As esperas falam na região viva da página, fora daqui.
    case "checandoCamera":
    case "iniciando":
    case "conferindo":
    case "falhaDoDetector":
    case "desfecho":
    case "documento":
    case "enviandoDocumento":
      return null;
  }
}
