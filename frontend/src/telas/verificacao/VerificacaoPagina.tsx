import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, useState, type ReactElement } from "react";
import { useLoaderData } from "react-router";
import type { Conta } from "../../api/estadoDaConta";
import { ErroHttp } from "../../api/http";
import type { VersoesLegais } from "../../api/legal";
import { concluirSelfie, iniciarSessao, type Finalidade, type SituacaoDaVerificacao } from "../../api/verificacao";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TITULO_DA_CASCA } from "../../componentes/estilos";
import { consultaDaConta, consultaDaVerificacao } from "../../rotas/consultas";
import { CASCA, textoDeEspera } from "../conta/textos";
import { checarCamera, navegadorTemSuporte, type MotivoSemCamera, type ResultadoDaCamera } from "./camera";
import { CapturaDeVida } from "./CapturaDeVida";
import { Desfecho, DesfechoDaVerificacao, Repeticao, VoltarAoTerraco } from "./Desfecho";
import { etapaInicial, proximaEtapa, recusaDaConferencia, type Etapa, type Evento } from "./etapas";
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

/** As etapas que trazem o próprio título (o Desfecho), que recebe o foco no lugar do h1. */
function temTituloProprio(etapa: Etapa): boolean {
  return (etapa.etapa === "desfecho" && etapa.tipo !== "sessaoInvalida") || etapa.etapa === "documento";
}

/**
 * Cada etapa tem a sua ação de entrada: a checagem da câmera roda em `checandoCamera`, o POST da
 * sessão em `iniciando` e o do resultado em `conferindo`. A sessão (com a credencial curta) só entra
 * no redutor: as mutações devolvem nada, e o cache de mutações nunca a vê. As tentativas começam na
 * situação, caem a cada sessão de selfie aberta (o servidor reserva a tentativa ali) e seguem o Desfecho.
 */
function Etapas({
  inicial,
  situacao,
  emailDoEncarregado,
  navegarParaPagina,
  checar,
  temSuporte,
}: PropsDasEtapas): ReactElement {
  // A etapa de abertura, guardada: o título de um desfecho só toma o foco quando a pessoa chegou nele.
  const [abertura] = useState(inicial);
  const [etapa, despachar] = useReducer(proximaEtapa, abertura);
  const [tentativas, definirTentativas] = useState(situacao.tentativasRestantesHoje);
  const clienteDeConsulta = useQueryClient();
  const titulo = useRef<HTMLHeadingElement>(null);
  const primeiraEtapa = useRef(true);
  // Travas síncronas dos POSTs: cada uma abre só quando a etapa sai de `iniciando` ou de `conferindo`.
  const iniciando = useRef(false);
  const conferindo = useRef(false);

  const sessao = useMutation({
    mutationFn: async (finalidade: Finalidade) => {
      const criada = await iniciarSessao(finalidade);
      if (finalidade === "SELFIE") {
        definirTentativas((restantes) => Math.max(0, restantes - 1));
      }
      despachar({ tipo: "sessaoCriada", sessao: criada });
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

  const conferencia = useMutation({
    mutationFn: async (sessaoId: string) => {
      const desfecho = await concluirSelfie(sessaoId);
      definirTentativas(desfecho.tentativasRestantesHoje);
      despachar({ tipo: "conferido", desfecho });
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaVerificacao.queryKey });
    },
    gcTime: 0,
    onError: (erro, sessaoId) => {
      if (erro instanceof ErroHttp && CODIGOS_DE_RECARGA.includes(erro.codigo)) {
        navegarParaPagina(CAMINHO_DA_CONTA);
        return;
      }
      // Uma recusa que vira desfecho (a mesma decisão do redutor) diz que a situação no servidor mudou.
      const resultado = recusaDaConferencia(sessaoId, erro);
      if (resultado.etapa === "desfecho") {
        // O servidor encerrou o cadastro do menor: a conta lida antes não vale mais.
        if (resultado.tipo === "menor") {
          clienteDeConsulta.removeQueries({ queryKey: consultaDaConta.queryKey, exact: true });
        }
        void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaVerificacao.queryKey });
      }
      despachar({ tipo: "conferenciaRecusada", erro });
    },
  });
  const conferir = conferencia.mutate;

  const tituloProprio = temTituloProprio(etapa);
  useEffect(() => {
    if (primeiraEtapa.current) {
      primeiraEtapa.current = false;
      return;
    }
    if (!tituloProprio) {
      titulo.current?.focus();
    }
  }, [etapa.etapa, tituloProprio]);

  useEffect(() => {
    if (etapa.etapa !== "iniciando") {
      iniciando.current = false;
    }
    if (etapa.etapa !== "conferindo") {
      conferindo.current = false;
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
    if (etapa.etapa === "conferindo" && !conferindo.current) {
      conferindo.current = true;
      conferir(etapa.sessaoId);
    }
    return undefined;
  }, [etapa, checar, abrirSessao, conferir]);

  const espera = textoDaEspera(etapa);
  const ocupado = etapa.etapa === "checandoCamera" || etapa.etapa === "iniciando" || etapa.etapa === "conferindo";

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
          abertura={etapa === abertura}
          situacao={situacao}
          tentativas={tentativas}
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
  /** Ainda na etapa em que a página abriu: o título do desfecho não toma o foco. */
  abertura: boolean;
  situacao: SituacaoDaVerificacao;
  tentativas: number;
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
    case "conferindo":
      return VERIFICACAO_DE_IDADE.conferindo;
    case "captura":
    case "explicacao":
    case "semCamera":
    case "falhaDoDetector":
    case "desfecho":
    case "documento":
    case "enviandoDocumento":
    case "limite":
    case "indisponivel":
      return null;
  }
}

interface PropsDaFalha {
  texto: string;
  aoRepetir: () => void;
  /** Quando a repetição gasta uma tentativa: mostra quantas sobram, e o limite do dia quando acabaram. */
  tentativas?: number;
}

function FalhaComRepeticao({ texto, aoRepetir, tentativas }: PropsDaFalha): ReactElement {
  return (
    <div className="mt-(--e-5) grid gap-(--e-4)">
      <p role="alert" className={AVISO}>
        {texto}
      </p>
      {tentativas === undefined ? (
        <button type="button" className={BOTAO_SECUNDARIO_SEM_MARGEM} onClick={aoRepetir}>
          {VERIFICACAO_DE_IDADE.tentarDeNovo}
        </button>
      ) : (
        <Repeticao tentativas={tentativas} aoRepetir={aoRepetir} />
      )}
    </div>
  );
}

function ConteudoDaEtapa({
  etapa,
  abertura,
  situacao,
  tentativas,
  emailDoEncarregado,
  temSuporte,
  despachar,
}: PropsDoConteudo): ReactElement | null {
  const tentarDeNovo = () => {
    despachar({ tipo: "tentarDeNovo" });
  };
  const novaSelfie = () => {
    despachar({ tipo: "comecar", modo: situacao.modo });
  };
  switch (etapa.etapa) {
    case "explicacao":
      return (
        <Explicacao
          tentativas={tentativas}
          emailDoEncarregado={emailDoEncarregado}
          // Sem a AWS, o detector falso não usa câmera nem wasm.
          temSuporte={situacao.modo === "falso" || temSuporte()}
          aoComecar={novaSelfie}
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
          <VoltarAoTerraco />
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
    case "falhaDoDetector":
      return (
        <FalhaComRepeticao
          texto={VERIFICACAO_DE_IDADE.falhas[etapa.falha]}
          tentativas={tentativas}
          aoRepetir={novaSelfie}
        />
      );
    case "desfecho":
      if (etapa.tipo === "sessaoInvalida") {
        // Volta para a explicação: o consentimento vale para uma selfie nova.
        return <FalhaComRepeticao texto={VERIFICACAO_DE_IDADE.sessaoInvalida} aoRepetir={tentarDeNovo} />;
      }
      return (
        <DesfechoDaVerificacao
          tipo={etapa.tipo}
          focar={!abertura}
          tentativas={tentativas}
          emailDoEncarregado={emailDoEncarregado}
          aoRepetir={novaSelfie}
        />
      );
    case "documento":
      // O envio das fotos completa esta etapa; aqui só o título.
      return <Desfecho titulo={VERIFICACAO_DE_IDADE.documento.titulo} focar={!abertura} />;
    // As esperas falam na região viva da página, fora daqui.
    case "checandoCamera":
    case "iniciando":
    case "conferindo":
    case "enviandoDocumento":
      return null;
  }
}
