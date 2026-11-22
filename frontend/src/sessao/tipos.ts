// Tipos da sessão do terraço, compartilhados pelo controlador (sessao.ts) e pelos componentes.

import type { Momento } from "../dominio/dados";
import type { Visao } from "../dominio/visao";

/** As telas saem da própria união do estado: uma tela nova só precisa entrar em EstadoDaTela. */
export type Tela = EstadoDaTela["tela"];

/** Ação em envio; enquanto uma está em voo, as outras são ignoradas. */
export type Acao = "entrada" | "pecas" | "retirar" | "sinal" | "pronto" | "ajustar" | "sair";

/** Cada .focus() do main.js antigo vira um pedido, aplicado depois do render. */
export type PedidoDeFoco =
  | { alvo: "apelido" | "link-convite" | "titulo-encerrado" | "sair" | "continuar" | "primeiro-lugar" }
  | { alvo: "item"; peca: string }
  /** Primeiro lugar livre ou, se não houver, o item da peça. */
  | { alvo: "apos-recusa"; peca: string }
  /** O botão da peça na cena (a do parceiro, depois de sugerir ou cancelar a sugestão). */
  | { alvo: "peca-na-cena"; peca: string };

/** Cada tela só carrega o que existe nela (State com tipo selado). */
export type EstadoDaTela =
  | { tela: "carregando" }
  /** trocaDeSala: a entrada veio de um convite para outra sala, com esta ainda aberta. */
  | { tela: "entrada"; comConvite: boolean; trocaDeSala: boolean; erroApelido: string }
  | { tela: "convite"; visao: Visao }
  /** sugerindo: a peça do parceiro que você está sugerindo mover; destaques: peças em destaque na cena. */
  | {
      tela: "montagem";
      visao: Visao;
      selecionada: string | null;
      sugerindo: string | null;
      destaques: readonly string[];
      instrucao: string;
    }
  | { tela: "encerrado"; titulo: string; texto: string };

export type EstadoDaSessao = EstadoDaTela & {
  enviando: Acao | null;
  /**
   * Sem visão da sala, a cena fica vazia; depois de um erro fora da API, a tela da vez fica parada
   * com a última visão, mas as peças saem da cena (o cena.limpar() do main.js antigo).
   */
  cenaVazia: boolean;
  confirmandoSaida: boolean;
  somLigado: boolean;
  momento: Momento;
  ladoTelescopio: string | null;
};
