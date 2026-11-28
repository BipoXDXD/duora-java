// Textos fixos das telas de perfil. As mensagens de negócio (validação do servidor) vêm da API.

import type { Intencao, Pronomes } from "../../api/perfil";

/** A navegação entre as telas de perfil, no topo de cada uma. */
export const NAVEGACAO_DO_PERFIL = {
  rotulo: "Seu perfil",
  meuPerfil: "Meu perfil",
  preferencias: "Preferências",
  namoro: "Namoro",
  orientacao: "Orientação",
  minhaConta: "Minha conta",
  voltarAoTerraco: "Voltar ao terraço",
} as const;

export const MEU_PERFIL = {
  titulo: "Meu perfil",
  tituloDaAba: "Meu perfil · Duora",
  introducao: "Seu perfil é o que as pessoas veem quando encontram você.",
  rotuloDoApelido: "Apelido",
  dicaDoApelido: "Até 20 caracteres.",
  rotuloDosPronomes: "Pronomes (opcional)",
  naoMostrar: "Não mostrar",
  rotuloDaDescricaoDosPronomes: "Seus pronomes",
  dicaDaDescricaoDosPronomes: "Até 20 caracteres.",
  rotuloDasIntencoes: "Como você quer usar o Duora",
  rotuloDaBio: "Sobre você",
  dicaDaBio: "Opcional.",
  salvar: "Salvar perfil",
  salvando: "Salvando…",
  avisoDeTirarNamoro: "Ao salvar sem Namoro, apagamos seu gênero e quem você quer conhecer.",
  salvo: "Perfil salvo.",
  versaoObsoleta: "Seu perfil mudou em outro aparelho. Confira e salve de novo.",
  falha: "Não conseguimos salvar seu perfil. Tente de novo em instantes.",
  tituloDoCartao: "Como os outros veem você",
  explicacaoDoCartao: "Quem encontra você vê a foto borrada. A nítida fica para suas conexões.",
  contador: (caracteres: number, maximo: number) => `${String(caracteres)}/${String(maximo)}`,
} as const;

/** Os rótulos das escolhas, na ordem do contrato. */
export const ROTULO_DOS_PRONOMES = {
  ELA_DELA: "ela/dela",
  ELE_DELE: "ele/dele",
  ELU_DELU: "elu/delu",
  OUTROS: "Prefiro descrever",
} as const satisfies Record<Pronomes, string>;

export const ROTULO_DAS_INTENCOES = {
  AMIZADE: "Amizade",
  NAMORO: "Namoro",
} as const satisfies Record<Intencao, string>;

/** As recusas do formulário antes do envio. */
export const ERROS_DO_PERFIL = {
  semApelido: "Escreva um apelido.",
  apelidoLongo: "Use até 20 caracteres.",
  descricaoDosPronomes: "Escreva seus pronomes com até 20 caracteres.",
  semIntencao: "Marque Amizade, Namoro ou as duas.",
  bioLonga: "Use até 280 caracteres.",
} as const;

export const PREFERENCIAS = {
  titulo: "Preferências de encontro",
  tituloDaAba: "Preferências de encontro · Duora",
} as const;

export const NAMORO = {
  titulo: "Para namoro",
  tituloDaAba: "Para namoro · Duora",
} as const;

export const ORIENTACAO = {
  titulo: "Orientação",
  tituloDaAba: "Orientação · Duora",
} as const;

export const PERFIL_DE_OUTRA_PESSOA = {
  tituloDaAba: (apelido: string) => `${apelido} · Duora`,
  indisponivel: "Perfil indisponível",
  tituloDaAbaIndisponivel: "Perfil indisponível · Duora",
} as const;
