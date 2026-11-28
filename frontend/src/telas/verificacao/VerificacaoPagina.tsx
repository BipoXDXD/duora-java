import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useEffect, useReducer, useRef, useState, type ReactElement } from "react";
import { useLoaderData } from "react-router";
import type { Conta } from "../../api/estadoDaConta";
import { ErroHttp } from "../../api/http";
import type { VersoesLegais } from "../../api/legal";
import {
  concluirSelfie,
  enviarDocumento,
  iniciarSessao,
  type Finalidade,
  type SituacaoDaVerificacao,
} from "../../api/verificacao";
import { BOTAO_SECUNDARIO_SEM_MARGEM, TITULO_DA_CASCA } from "../../componentes/estilos";
import { consultaDaConta, consultaDaVerificacao } from "../../rotas/consultas";
import { CASCA, textoDeEspera } from "../conta/textos";
import { checarCamera, navegadorTemSuporte, type MotivoSemCamera, type ResultadoDaCamera } from "./camera";
import { CapturaDeVida } from "./CapturaDeVida";
import { DesfechoDaVerificacao, Repeticao, VoltarAoTerraco } from "./Desfecho";
import { EnvioDeDocumento } from "./EnvioDeDocumento";
import {
  etapaInicial,
  proximaEtapa,
  recusaDaConferencia,
  recusaDoEnvio,
  type Etapa,
  type Evento,
  type FotosDoDocumento,
} from "./etapas";
import { Explicacao } from "./Explicacao";
import { VERIFICACAO_DE_IDADE, VERSAO_DO_CONSENTIMENTO } from "./textos";

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

/**
 * Recusas que dizem que esta página não vale mais para a conta: Minha conta leva à tela certa. A
 * recarga completa também traz o texto novo do consentimento, quando ele mudou.
 */
const CODIGOS_DE_RECARGA: readonly string[] = [
  "VERIFICACAO_FORA_DE_ORDEM",
  "NAO_AUTENTICADO",
  "CONTA_INDISPONIVEL",
  "CONSENTIMENTO_DESATUALIZADO",
];
// Recusas depois das quais a página não sabe o que o servidor gastou: relê as contagens nele.
const CODIGOS_DE_RESSINCRONIA: readonly string[] = [
  "SESSAO_DE_VERIFICACAO_INVALIDA",
  "REFAZER_PROVA_DE_VIDA",
  "SELFIE_NAO_DEU_CERTO",
];

const CAMINHO_DA_CONTA = "/conta";

interface PedidoDeEnvio {
  sessaoId: string;
  fotos: FotosDoDocumento;
}

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
 * O envio do documento sai em `enviandoDocumento`, logo depois da selfie dele; as fotos vão por uma ref,
 * nunca como variável da mutação. Os envios seguem a mesma regra das tentativas, pela sessão DOCUMENTO.
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
  const [envios, definirEnvios] = useState(situacao.enviosDeDocumentoRestantesHoje);
  const clienteDeConsulta = useQueryClient();
  const titulo = useRef<HTMLHeadingElement>(null);
  const primeiraEtapa = useRef(true);
  // Travas síncronas dos POSTs: cada uma abre só quando a etapa sai de `iniciando` ou de `conferindo`.
  const iniciando = useRef(false);
  const conferindo = useRef(false);
  const enviando = useRef(false);
  // O multipart em curso; some quando o envio termina, com sucesso ou não.
  const pedidoDeEnvio = useRef<PedidoDeEnvio | null>(null);
  // Sobe a cada sessão aberta: a releitura das contagens que começou antes dela já não vale.
  const sessoesAbertas = useRef(0);

  const sessao = useMutation({
    mutationFn: async (finalidade: Finalidade) => {
      const criada = await iniciarSessao(finalidade, VERSAO_DO_CONSENTIMENTO);
      const contador = finalidade === "SELFIE" ? definirTentativas : definirEnvios;
      sessoesAbertas.current += 1;
      contador((restantes) => Math.max(0, restantes - 1));
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

  /**
   * Relê a situação no servidor e adota as contagens dele. Vale quando a página não sabe o que o
   * servidor gastou: os 409 da selfie vencida ou que não serviu (só a expirada devolve o envio,
   * D14-bis) e o 404 de sessão. Uma sessão aberta no meio da releitura já descontou a dela, e a releitura é descartada.
   */
  const ressincronizarContagens = (): void => {
    const antes = sessoesAbertas.current;
    clienteDeConsulta
      .query({ ...consultaDaVerificacao, staleTime: 0 })
      .then((atual) => {
        if (sessoesAbertas.current === antes) {
          definirTentativas(atual.tentativasRestantesHoje);
          definirEnvios(atual.enviosDeDocumentoRestantesHoje);
        }
      })
      // Sem a releitura, a página segue com as contagens que já tinha; o servidor ainda decide o limite.
      .catch(() => undefined);
  };

  /**
   * A recusa da conferência ou do envio, já decidida pelo redutor (`resultado`). As de recarga vão para
   * Minha conta; um desfecho diz que a situação no servidor mudou; o menor some com a conta lida antes.
   * O 404 de sessão (no envio, pode ser um reenvio do que o servidor já concluiu ou descartou) e a selfie
   * vencida relêem as contagens no servidor.
   */
  const tratarRecusa = (erro: unknown, resultado: Etapa, aoRecusar: () => void): void => {
    if (erro instanceof ErroHttp && CODIGOS_DE_RECARGA.includes(erro.codigo)) {
      navegarParaPagina(CAMINHO_DA_CONTA);
      return;
    }
    const codigo = erro instanceof ErroHttp ? erro.codigo : null;
    if (codigo !== null && CODIGOS_DE_RESSINCRONIA.includes(codigo)) {
      ressincronizarContagens();
    } else if (resultado.etapa === "desfecho") {
      if (resultado.tipo === "menor") {
        clienteDeConsulta.removeQueries({ queryKey: consultaDaConta.queryKey, exact: true });
      }
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaVerificacao.queryKey });
    }
    aoRecusar();
  };

  const conferencia = useMutation({
    mutationFn: async (sessaoId: string) => {
      const desfecho = await concluirSelfie(sessaoId);
      definirTentativas(desfecho.tentativasRestantesHoje);
      despachar({ tipo: "conferido", desfecho });
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaVerificacao.queryKey });
    },
    gcTime: 0,
    onError: (erro, sessaoId) => {
      tratarRecusa(erro, recusaDaConferencia(sessaoId, erro), () => {
        despachar({ tipo: "conferenciaRecusada", erro });
      });
    },
  });
  const conferir = conferencia.mutate;

  const envio = useMutation({
    mutationFn: async () => {
      const pedido = pedidoDeEnvio.current;
      if (pedido === null) {
        return;
      }
      const desfecho = await enviarDocumento({
        sessaoId: pedido.sessaoId,
        frente: pedido.fotos.frente,
        verso: pedido.fotos.verso,
      });
      definirEnvios(desfecho.enviosDeDocumentoRestantesHoje);
      despachar({ tipo: "enviado", desfecho });
      void clienteDeConsulta.invalidateQueries({ queryKey: consultaDaVerificacao.queryKey });
    },
    gcTime: 0,
    onError: (erro) => {
      const pedido = pedidoDeEnvio.current;
      if (pedido === null) {
        return;
      }
      tratarRecusa(erro, recusaDoEnvio(pedido, erro), () => {
        despachar({ tipo: "envioRecusado", erro });
      });
    },
    onSettled: () => {
      pedidoDeEnvio.current = null;
    },
  });
  const enviar = envio.mutate;

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
    if (etapa.etapa !== "enviandoDocumento") {
      enviando.current = false;
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
    if (etapa.etapa === "enviandoDocumento" && !enviando.current) {
      enviando.current = true;
      pedidoDeEnvio.current = { sessaoId: etapa.sessaoId, fotos: etapa.fotos };
      enviar();
    }
    return undefined;
  }, [etapa, checar, abrirSessao, conferir, enviar]);

  const espera = textoDaEspera(etapa);
  const ocupado =
    etapa.etapa === "checandoCamera" ||
    etapa.etapa === "iniciando" ||
    etapa.etapa === "conferindo" ||
    etapa.etapa === "enviandoDocumento";

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
          envios={envios}
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
  envios: number;
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
    case "enviandoDocumento":
      return VERIFICACAO_DE_IDADE.documento.enviando;
    case "captura":
    case "explicacao":
    case "semCamera":
    case "falhaDoDetector":
    case "desfecho":
    case "documento":
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
  /** O texto da contagem, quando o que a repetição gasta é um envio do documento. */
  contagem?: (n: number) => string;
}

function FalhaComRepeticao({ texto, aoRepetir, tentativas, contagem }: PropsDaFalha): ReactElement {
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
        <Repeticao tentativas={tentativas} aoRepetir={aoRepetir} {...(contagem === undefined ? {} : { contagem })} />
      )}
    </div>
  );
}

function ConteudoDaEtapa({
  etapa,
  abertura,
  situacao,
  tentativas,
  envios,
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
      return (
        <FalhaComRepeticao
          texto={
            etapa.repetir.tipo === "enviar"
              ? VERIFICACAO_DE_IDADE.documento.conferenciaFora
              : VERIFICACAO_DE_IDADE.servicoFora
          }
          aoRepetir={tentarDeNovo}
        />
      );
    case "captura":
      // O detector vem do modo que o servidor mandou na sessão (D-F2).
      return (
        <CapturaDeVida
          sessao={etapa.sessao}
          aoConcluir={() => {
            despachar({ tipo: "concluiu" });
          }}
          aoFalhar={(falha) => {
            // Os envios já contam a sessão desta selfie: zero quer dizer que era o último de hoje.
            despachar({ tipo: "falhou", falha, semEnvio: etapa.finalidade === "DOCUMENTO" && envios === 0 });
          }}
          aoCancelar={() => {
            despachar({ tipo: "cancelou" });
          }}
        />
      );
    case "falhaDoDetector":
      // A selfie do documento gasta um envio, não uma tentativa: a contagem mostra os envios.
      return etapa.finalidade === "DOCUMENTO" ? (
        <FalhaComRepeticao
          texto={VERIFICACAO_DE_IDADE.falhas[etapa.falha]}
          // Na falha de carregamento, a repetição reaproveita a sessão: não gasta envio, não há contagem.
          {...(etapa.sessao === undefined
            ? { tentativas: envios, contagem: VERIFICACAO_DE_IDADE.documento.envios }
            : {})}
          aoRepetir={novaSelfie}
        />
      ) : (
        <FalhaComRepeticao
          texto={VERIFICACAO_DE_IDADE.falhas[etapa.falha]}
          // Na falha de carregamento, a repetição reaproveita a sessão: não gasta tentativa, não há contagem.
          {...(etapa.sessao === undefined ? { tentativas } : {})}
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
      return (
        <EnvioDeDocumento
          frente={etapa.frente}
          verso={etapa.verso}
          aviso={etapa.aviso}
          envios={envios}
          focar={!abertura}
          aoEscolher={(lado, foto) => {
            despachar({ tipo: "foto", lado, foto });
          }}
          aoFazerSelfie={novaSelfie}
        />
      );
    // As esperas falam na região viva da página, fora daqui.
    case "checandoCamera":
    case "iniciando":
    case "conferindo":
    case "enviandoDocumento":
      return null;
  }
}
