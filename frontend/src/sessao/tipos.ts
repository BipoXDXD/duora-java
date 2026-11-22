// Tipos da sessão do terraço, compartilhados pelo controlador (sessao.ts) e pelos componentes.

import type { Visao } from "../dominio/visao";

export type Tela = "carregando" | "entrada" | "convite" | "montagem" | "encerrado";

/** Ação em envio; enquanto uma está em voo, as outras são ignoradas. */
export type Acao = "entrada" | "pecas" | "retirar" | "sinal" | "pronto" | "ajustar" | "sair";

/** Cada .focus() do main.js antigo vira um pedido, aplicado depois do render. */
export type PedidoDeFoco =
  | { alvo: "apelido" | "link-convite" | "titulo-encerrado" | "sair" | "continuar" | "primeiro-lugar" }
  | { alvo: "item"; peca: string }
  /** Primeiro lugar livre ou, se não houver, o item da peça. */
  | { alvo: "apos-recusa"; peca: string };

/** Cada tela só carrega o que existe nela (State com tipo selado). */
export type EstadoDaTela =
  | { tela: "carregando" }
  | { tela: "entrada"; comConvite: boolean; erroApelido: string; aviso: string | null }
  | { tela: "convite"; visao: Visao }
  | { tela: "montagem"; visao: Visao; selecionada: string | null; instrucao: string }
  | { tela: "encerrado"; texto: string };

export type EstadoDaSessao = EstadoDaTela & {
  enviando: Acao | null;
  confirmandoSaida: boolean;
  somLigado: boolean;
  momento: "crepusculo" | "noite";
  ladoTelescopio: string | null;
};
