// Cliente da API de perfil (Etapa 3) sobre o transporte de http.ts. Cada resposta passa por um
// esquema valibot com os enums canônicos: fora do formato vira ErroHttp RESPOSTA_INVALIDA, sem o
// corpo junto. Chaves extras são descartadas (v.object), para o servidor poder crescer sem quebrar.

import * as v from "valibot";
import { ErroHttp, requisitarHttp } from "./http";

const INTENCOES = ["AMIZADE", "NAMORO"] as const;
const PRONOMES = ["ELA_DELA", "ELE_DELE", "ELU_DELU", "OUTROS"] as const;
const RAIOS = ["ATE_5_KM", "ATE_20_KM", "ATE_50_KM", "SEM_LIMITE"] as const;
const GENEROS = ["MULHER", "HOMEM", "PESSOA_NAO_BINARIA", "OUTRO"] as const;
const ORIENTACOES = [
  "HETEROSSEXUAL",
  "GAY",
  "LESBICA",
  "BISSEXUAL",
  "PANSEXUAL",
  "ASSEXUAL",
  "DEMISSEXUAL",
  "QUEER",
  "QUESTIONANDO",
  "OUTRA",
] as const;
const PENDENCIAS = ["PERFIL", "DADOS_DE_NAMORO"] as const;

export type Intencao = (typeof INTENCOES)[number];
export type Pronomes = (typeof PRONOMES)[number];
export type Raio = (typeof RAIOS)[number];
export type Genero = (typeof GENEROS)[number];
export type Orientacao = (typeof ORIENTACOES)[number];
export type Pendencia = (typeof PENDENCIAS)[number];

const Inteiro = v.pipe(v.number(), v.integer());
const Versao = v.nullable(v.pipe(Inteiro, v.minValue(1)));

const EsquemaUrlsDaFoto = v.object({ nitida: v.string(), borrada: v.string() });

const EsquemaMeuPerfil = v.object({
  versao: Versao,
  apelido: v.nullable(v.string()),
  pronomes: v.nullable(v.picklist(PRONOMES)),
  descricaoPronomes: v.nullable(v.string()),
  intencoes: v.array(v.picklist(INTENCOES)),
  bio: v.string(),
  pausada: v.boolean(),
  prontoParaFila: v.boolean(),
  pendencias: v.array(v.picklist(PENDENCIAS)),
  foto: v.object({
    aprovada: v.nullable(EsquemaUrlsDaFoto),
    pendente: v.nullable(EsquemaUrlsDaFoto),
    recusadaEm: v.nullable(v.string()),
  }),
});

const EsquemaPreferencias = v.object({
  versao: Versao,
  idadeMinima: Inteiro,
  idadeMaxima: Inteiro,
  raio: v.picklist(RAIOS),
  raioObrigatorio: v.boolean(),
});

const EsquemaLocalizacao = v.object({ consentida: v.boolean(), atualizadaEm: v.nullable(v.string()) });

const EsquemaNamoro = v.object({
  genero: v.picklist(GENEROS),
  descricaoGenero: v.nullable(v.string()),
  interesses: v.array(v.picklist(GENEROS)),
  consentidoEm: v.string(),
});

/** O GET do namoro sem dados gravados: 200 com `genero` nulo. */
const EsquemaSemNamoro = v.object({ genero: v.null() });

const EsquemaOrientacao = v.object({
  orientacao: v.picklist(ORIENTACOES),
  descricaoOrientacao: v.nullable(v.string()),
  mostrarOrientacao: v.boolean(),
  consentidaEm: v.string(),
});

/** O GET da orientação sem dados gravados: 200 com `orientacao` nula. */
const EsquemaSemOrientacao = v.object({ orientacao: v.null() });

const EsquemaPerfilPublico = v.object({
  apelido: v.string(),
  foto: v.nullable(v.string()),
  pronomes: v.nullable(v.string()),
  verificado: v.boolean(),
});

export type UrlsDaFoto = v.InferOutput<typeof EsquemaUrlsDaFoto>;
export type MeuPerfil = v.InferOutput<typeof EsquemaMeuPerfil>;
export type FotoDoPerfil = MeuPerfil["foto"];
export type PreferenciasDeEncontro = v.InferOutput<typeof EsquemaPreferencias>;
export type SituacaoDaLocalizacao = v.InferOutput<typeof EsquemaLocalizacao>;
export type DadosDeNamoro = v.InferOutput<typeof EsquemaNamoro>;
export type DadosDeOrientacao = v.InferOutput<typeof EsquemaOrientacao>;
export type PerfilPublico = v.InferOutput<typeof EsquemaPerfilPublico>;

export interface EdicaoDoPerfil {
  versao: number | null;
  apelido: string;
  pronomes: Pronomes | null;
  descricaoPronomes: string | null;
  intencoes: Intencao[];
  bio: string;
}

export interface Coordenada {
  latitude: number;
  longitude: number;
}

export interface EdicaoDoNamoro {
  genero: Genero;
  descricaoGenero: string | null;
  interesses: Genero[];
  consentimento: true;
}

export interface EdicaoDaOrientacao {
  orientacao: Orientacao;
  descricaoOrientacao: string | null;
  mostrarOrientacao: boolean;
  consentimento: true;
}

function ler<S extends v.GenericSchema>(esquema: S, status: number, dados: unknown): v.InferOutput<S> {
  const resultado = v.safeParse(esquema, dados);
  if (!resultado.success) {
    throw new ErroHttp({ status, codigo: "RESPOSTA_INVALIDA" });
  }
  return resultado.output;
}

async function obter<S extends v.GenericSchema>(caminho: string, esquema: S): Promise<v.InferOutput<S>> {
  const resposta = await requisitarHttp("GET", caminho);
  return ler(esquema, resposta.status, resposta.dados);
}

async function gravar<S extends v.GenericSchema>(
  caminho: string,
  corpo: unknown,
  esquema: S,
): Promise<v.InferOutput<S>> {
  const resposta = await requisitarHttp("PUT", caminho, { corpo });
  return ler(esquema, resposta.status, resposta.dados);
}

async function semResposta(metodo: "POST" | "PUT" | "DELETE", caminho: string, corpo?: unknown): Promise<void> {
  await requisitarHttp(metodo, caminho, corpo === undefined ? {} : { corpo });
}

/** 409 VERSAO_OBSOLETA: o perfil ou os ajustes mudaram enquanto a pessoa editava. */
export function versaoObsoleta(erro: unknown): boolean {
  return erro instanceof ErroHttp && erro.status === 409 && erro.codigo === "VERSAO_OBSOLETA";
}

export function obterMeuPerfil(): Promise<MeuPerfil> {
  return obter("/perfil", EsquemaMeuPerfil);
}

/** Manda só os campos do contrato; `versao` nula cria o perfil. */
export function salvarPerfil(e: EdicaoDoPerfil): Promise<MeuPerfil> {
  const { versao, apelido, pronomes, descricaoPronomes, intencoes, bio } = e;
  return gravar("/perfil", { versao, apelido, pronomes, descricaoPronomes, intencoes, bio }, EsquemaMeuPerfil);
}

const EsquemaFotoEnviada = v.object({ estado: v.picklist(["PENDENTE", "APROVADA"]) });

/**
 * 202 com o estado em que a foto entrou: APROVADA se a moderação automática a liberou, senão
 * PENDENTE (espera a revisão). O próximo GET do perfil a mostra. O nome fixo evita mandar ao
 * servidor o nome do arquivo no aparelho.
 */
export async function enviarFoto(arquivo: Blob, chave: string): Promise<{ aprovada: boolean }> {
  const formulario = new FormData();
  formulario.append("arquivo", arquivo, "foto");
  const resposta = await requisitarHttp("POST", "/perfil/foto", {
    formulario,
    cabecalhos: { "Idempotency-Key": chave },
  });
  return { aprovada: ler(EsquemaFotoEnviada, resposta.status, resposta.dados).estado === "APROVADA" };
}

/** Apaga a foto aprovada, a pendente e a marca de recusa; o perfil volta à silhueta. */
export function removerFoto(): Promise<void> {
  return semResposta("DELETE", "/perfil/foto");
}

export function pausarConta(): Promise<void> {
  return semResposta("POST", "/perfil/pausa");
}

export function voltarAAparecer(): Promise<void> {
  return semResposta("DELETE", "/perfil/pausa");
}

export function obterPreferencias(): Promise<PreferenciasDeEncontro> {
  return obter("/perfil/preferencias", EsquemaPreferencias);
}

export function salvarPreferencias(p: PreferenciasDeEncontro): Promise<PreferenciasDeEncontro> {
  const { versao, idadeMinima, idadeMaxima, raio, raioObrigatorio } = p;
  return gravar(
    "/perfil/preferencias",
    { versao, idadeMinima, idadeMaxima, raio, raioObrigatorio },
    EsquemaPreferencias,
  );
}

export function obterLocalizacao(): Promise<SituacaoDaLocalizacao> {
  return obter("/perfil/localizacao", EsquemaLocalizacao);
}

/** Só latitude e longitude saem, mesmo que o objeto traga mais (precisão, altitude). */
export function ativarLocalizacao(c: Coordenada): Promise<void> {
  return semResposta("PUT", "/perfil/localizacao", { latitude: c.latitude, longitude: c.longitude });
}

export function desativarLocalizacao(): Promise<void> {
  return semResposta("DELETE", "/perfil/localizacao");
}

export async function obterNamoro(): Promise<DadosDeNamoro | null> {
  const resposta = await requisitarHttp("GET", "/perfil/namoro");
  if (v.is(EsquemaSemNamoro, resposta.dados)) {
    return null;
  }
  return ler(EsquemaNamoro, resposta.status, resposta.dados);
}

export function salvarNamoro(d: EdicaoDoNamoro): Promise<DadosDeNamoro> {
  const { genero, descricaoGenero, interesses, consentimento } = d;
  return gravar("/perfil/namoro", { genero, descricaoGenero, interesses, consentimento }, EsquemaNamoro);
}

export function apagarNamoro(): Promise<void> {
  return semResposta("DELETE", "/perfil/namoro");
}

export async function obterOrientacao(): Promise<DadosDeOrientacao | null> {
  const resposta = await requisitarHttp("GET", "/perfil/orientacao");
  if (v.is(EsquemaSemOrientacao, resposta.dados)) {
    return null;
  }
  return ler(EsquemaOrientacao, resposta.status, resposta.dados);
}

export function salvarOrientacao(d: EdicaoDaOrientacao): Promise<DadosDeOrientacao> {
  const { orientacao, descricaoOrientacao, mostrarOrientacao, consentimento } = d;
  return gravar(
    "/perfil/orientacao",
    { orientacao, descricaoOrientacao, mostrarOrientacao, consentimento },
    EsquemaOrientacao,
  );
}

export function apagarOrientacao(): Promise<void> {
  return semResposta("DELETE", "/perfil/orientacao");
}

/** Recusas que viram "perfil indisponível": para quem vê, não há diferença entre elas. */
function perfilIndisponivel(erro: unknown): boolean {
  return (
    erro instanceof ErroHttp &&
    ((erro.status === 404 && erro.codigo === "PERFIL_INEXISTENTE") ||
      (erro.status === 400 && erro.codigo === "REQUISICAO_INVALIDA"))
  );
}

/**
 * 404 PERFIL_INEXISTENTE vira null (inexistente e sem permissão são iguais para quem vê), e o 400
 * REQUISICAO_INVALIDA também: o único 400 desta leitura é o id que não é UUID (um link truncado).
 */
export async function obterPerfilPublico(contaId: string): Promise<PerfilPublico | null> {
  try {
    return await obter(`/perfis/${encodeURIComponent(contaId)}`, EsquemaPerfilPublico);
  } catch (erro) {
    if (perfilIndisponivel(erro)) {
      return null;
    }
    throw erro;
  }
}
