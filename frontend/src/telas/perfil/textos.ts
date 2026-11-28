// Textos fixos das telas de perfil. As mensagens de negócio (validação do servidor) vêm da API.

import type { Genero, Intencao, Orientacao, Pronomes, Raio } from "../../api/perfil";

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

export const FOTO = {
  titulo: "Foto",
  alt: (apelido: string | null) => (apelido === null ? "Sua foto" : `Foto de ${apelido}`),
  semFoto: "Você ainda não tem foto. Até ter uma aprovada, os outros veem uma silhueta.",
  aguardando: "Aguardando aprovação. Até lá, os outros veem uma silhueta.",
  novaAguardando: "Foto nova aguardando aprovação. Até lá, vale a anterior.",
  recusada: "Sua última foto não foi aprovada.",
  politica:
    "Sem nudez, sexo, violência explícita ou conteúdo chocante. Toda foto passa por aprovação antes de aparecer.",
  dica: "JPEG, PNG ou WebP, até 5 MB.",
  escolher: "Escolher foto",
  escolherOutra: "Escolher outra",
  previa: "Prévia da foto escolhida",
  enviar: "Enviar foto",
  enviando: "Enviando…",
  enviada: "Foto enviada. Ela aparece depois da aprovação.",
  aprovada: "Foto aprovada. Ela já aparece no seu perfil.",
  remover: "Remover foto",
  removendo: "Removendo…",
  removida: "Foto removida. Outras pessoas voltam a ver uma silhueta.",
  removerTitulo: "Remover a foto?",
  removerTexto:
    "A foto sai do seu perfil e outras pessoas passam a ver uma silhueta. Você pode enviar outra quando quiser.",
  removerConfirmar: "Remover",
  removerFalhou: "Não conseguimos remover a foto agora. Tente de novo em instantes.",
  grande: "A foto pode ter até 5 MB. Escolha uma menor.",
  formato: "Envie uma foto em JPEG, PNG ou WebP.",
  invalida: "Não conseguimos abrir esta foto. Escolha outra.",
  indisponivel: "Não conseguimos enviar a foto agora. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
} as const;

export const PAUSA = {
  titulo: "Pausar a conta",
  explicacao:
    "Enquanto a conta estiver pausada, você não entra na fila nem recebe avisos de pessoas compatíveis. Suas conexões e conversas continuam.",
  pausar: "Pausar",
  pausando: "Pausando…",
  faixa: "Sua conta está pausada.",
  voltar: "Voltar a aparecer",
  voltando: "Voltando…",
  falha: "Não conseguimos mudar a pausa. Tente de novo em instantes.",
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
  apelidoInvisivel: "Tire os caracteres invisíveis do apelido. Eles costumam vir junto ao colar um texto.",
  descricaoDosPronomes: "Escreva seus pronomes com até 20 caracteres.",
  semIntencao: "Marque Amizade, Namoro ou as duas.",
  bioLonga: "Use até 280 caracteres.",
  bioInvisivel: "Tire os caracteres invisíveis da bio. Eles costumam vir junto ao colar um texto.",
} as const;

export const PREFERENCIAS = {
  titulo: "Preferências de encontro",
  tituloDaAba: "Preferências de encontro · Duora",
  rotuloDaIdade: "Idade de quem você quer conhecer",
  de: "De",
  ate: "Até",
  idadeInvalida: 'Use idades de 18 a 99, com "De" menor ou igual a "Até".',
  rotuloDaDistancia: "Distância",
  ativeALocalizacao: "Ative a localização para escolher uma distância.",
  rotuloDoComoUsar: "Como usar a distância",
  soDentroDoRaio: "Só pessoas dentro do raio",
  maisPertoPrimeiro: "Prefiro quem está mais perto, mas aceito mais longe",
  salvar: "Salvar preferências",
  salvando: "Salvando…",
  salvas: "Preferências salvas.",
  versaoObsoleta: "Suas preferências mudaram em outro aparelho. Confira e salve de novo.",
  falha: "Não conseguimos salvar suas preferências. Tente de novo em instantes.",
} as const;

export const ROTULO_DOS_RAIOS = {
  ATE_5_KM: "Até 5 km",
  ATE_20_KM: "Até 20 km",
  ATE_50_KM: "Até 50 km",
  SEM_LIMITE: "Sem limite",
} as const satisfies Record<Raio, string>;

export const LOCALIZACAO = {
  titulo: "Localização",
  explicacao: "Guardamos só uma área de cerca de 5 km, nunca o ponto exato.",
  usar: "Usar minha localização",
  esperando: "Esperando a permissão do navegador…",
  gravando: "Ativando a localização…",
  ativa: "Localização ativa (área aproximada)",
  desativar: "Desativar localização",
  desativando: "Desativando…",
  falhou: 'Não conseguimos sua localização. A distância fica em "Sem limite".',
  negada: "Para tentar de novo, libere a localização nas configurações do navegador.",
  desativada: 'Localização desativada. A distância voltou para "Sem limite".',
  falhaAoDesativar: "Não conseguimos desativar a localização. Tente de novo em instantes.",
} as const;

export const NAMORO = {
  titulo: "Para namoro",
  tituloDaAba: "Para namoro · Duora",
  desligado: "O namoro está desligado. Para usar, marque Namoro em Meu perfil.",
  irParaMeuPerfil: "Ir para Meu perfil",
  aviso:
    "Gênero e interesse são dados sensíveis. Usamos só para encontrar pessoas compatíveis e nunca mostramos a ninguém. Você pode apagar quando quiser.",
  rotuloDoGenero: "Seu gênero",
  dicaDoGenero: "Escolha a opção com que você se identifica.",
  rotuloDaDescricao: "Como você se descreve",
  dicaDaDescricao: "Até 40 caracteres.",
  rotuloDosInteresses: "Quem você quer conhecer",
  consentimento: "Concordo com o uso desses dados para o pareamento",
  concordouEm: (data: string) => `Você concordou em ${data}.`,
  salvar: "Salvar dados de namoro",
  salvando: "Salvando…",
  semConsentimento: "Marque a caixa de consentimento para salvar.",
  salvos: "Dados de namoro salvos.",
  falha: "Não conseguimos salvar seus dados de namoro. Tente de novo em instantes.",
  apagar: "Apagar dados de namoro",
  tituloDoApagar: "Apagar dados de namoro?",
  textoDoApagar:
    "Apagamos seu gênero e quem você quer conhecer, e Namoro sai das suas intenções. Para voltar, preencha e concorde de novo.",
  acaoDoApagar: "Apagar dados",
  apagando: "Apagando…",
  apagados: "Dados de namoro apagados.",
  falhaAoApagar: "Não conseguimos apagar seus dados de namoro. Tente de novo em instantes.",
} as const;

/** Os rótulos do gênero e de quem conhecer, na ordem do contrato (G1). */
export const ROTULO_DO_GENERO = {
  MULHER: "Mulher",
  HOMEM: "Homem",
  PESSOA_NAO_BINARIA: "Pessoa não binária",
  OUTRO: "Prefiro descrever",
} as const satisfies Record<Genero, string>;

export const ROTULO_DO_INTERESSE = {
  MULHER: "Mulheres",
  HOMEM: "Homens",
  PESSOA_NAO_BINARIA: "Pessoas não binárias",
  OUTRO: "Pessoas que se descrevem de outra forma",
} as const satisfies Record<Genero, string>;

/** As recusas do formulário de namoro antes do envio. */
export const ERROS_DO_NAMORO = {
  semGenero: "Escolha seu gênero.",
  descricao: "Escreva como você se descreve, com até 40 caracteres.",
  semInteresse: "Marque ao menos uma opção.",
} as const;

export const ORIENTACAO = {
  titulo: "Orientação",
  tituloDaAba: "Orientação · Duora",
  aviso:
    "Orientação é um dado sensível. É opcional, nunca entra no pareamento e só aparece para suas conexões se você quiser.",
  rotulo: "Sua orientação",
  rotuloDaDescricao: "Como você descreve sua orientação",
  dicaDaDescricao: "Até 40 caracteres.",
  mostrar: "Mostrar para minhas conexões",
  consentimento: "Concordo em guardar minha orientação no Duora",
  concordouEm: (data: string) => `Você concordou em ${data}.`,
  salvar: "Salvar orientação",
  salvando: "Salvando…",
  semConsentimento: "Marque a caixa de consentimento para salvar.",
  salva: "Orientação salva.",
  falha: "Não conseguimos salvar sua orientação. Tente de novo em instantes.",
  apagar: "Apagar orientação",
  apagando: "Apagando…",
  apagada: "Orientação apagada.",
  falhaAoApagar: "Não conseguimos apagar sua orientação. Tente de novo em instantes.",
} as const;

/** Os rótulos da orientação, na ordem do contrato (D4). */
export const ROTULO_DA_ORIENTACAO = {
  HETEROSSEXUAL: "Heterossexual",
  GAY: "Gay",
  LESBICA: "Lésbica",
  BISSEXUAL: "Bissexual",
  PANSEXUAL: "Pansexual",
  ASSEXUAL: "Assexual",
  DEMISSEXUAL: "Demissexual",
  QUEER: "Queer",
  QUESTIONANDO: "Questionando",
  OUTRA: "Prefiro descrever",
} as const satisfies Record<Orientacao, string>;

/** As recusas do formulário de orientação antes do envio. */
export const ERROS_DA_ORIENTACAO = {
  semOrientacao: "Escolha sua orientação.",
  descricao: "Escreva como você descreve sua orientação, com até 40 caracteres.",
} as const;

export const PERFIL_DE_OUTRA_PESSOA = {
  tituloDaAba: (apelido: string) => `${apelido} · Duora`,
  indisponivel: "Perfil indisponível",
  tituloDaAbaIndisponivel: "Perfil indisponível · Duora",
  explicacaoDoIndisponivel: "Este perfil não existe ou não está disponível.",
  minhaConta: "Minha conta",
  voltarAoTerraco: "Voltar ao terraço",
} as const;
