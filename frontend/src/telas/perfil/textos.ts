// Textos fixos das telas de perfil. As mensagens de negócio (validação do servidor) vêm da API.

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
