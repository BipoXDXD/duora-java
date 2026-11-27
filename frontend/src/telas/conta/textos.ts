// Textos fixos das telas de conta. As mensagens de negócio (sucesso, e-mail recusado) vêm do servidor.

/** O link de volta ao "/", nas telas sem próximo passo na casca. */
const VOLTAR_AO_TERRACO = "Voltar ao terraço";

/** A verificação de segurança (Turnstile), nas telas que a pedem antes de enviar. */
export const VERIFICACAO = {
  aguarde: "Aguarde a verificação de segurança acima.",
  falha: "Não conseguimos carregar a verificação de segurança.",
  tentarDeNovo: "Tentar de novo",
} as const;

export const ENTRAR = {
  titulo: "Entrar no Duora",
  tituloDaAba: "Entrar · Duora",
  rotuloDoEmail: "Seu e-mail",
  enviar: "Enviar link",
  enviando: "Enviando…",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar, 409): o link pode ter saído. */
  falhaNoEnvio: "Não conseguimos confirmar o envio do link. Tente de novo em instantes.",
} as const;

export const CONFIRMAR = {
  titulo: "Confirmar entrada",
  tituloDaAba: "Confirmar entrada · Duora",
  /** O POST só sai no clique: um leitor de e-mail que pré-abre o link não gasta o token. */
  instrucao: "Use o botão Entrar para abrir sua sessão neste navegador.",
  entrar: "Entrar",
  entrando: "Entrando…",
  semToken: "Não encontramos o código do link. Abra o link do e-mail de novo ou peça um novo.",
  pedirNovoLink: "Pedir novo link",
  /** Reservas para quando o servidor não manda a mensagem dele (ele sempre manda). */
  linkInvalido: "Este link expirou ou já foi usado. Peça um novo.",
  contaIndisponivel: "Esta conta não está disponível.",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar): o token pode não ter sido gasto. */
  falha: "Não conseguimos confirmar a entrada. Tente de novo em instantes.",
  /** A sessão abriu, mas a leitura da conta falhou: o token já foi gasto, então o caminho é o terraço. */
  falhaDepoisDaEntrada: "Sua entrada foi confirmada, mas a próxima página não carregou.",
  continuarNoTerraco: "Continuar no terraço",
} as const;

export const CADASTRO = {
  titulo: "Falta pouco",
  /** No desfecho sem volta (menor de idade, conta indisponível). */
  tituloNaoConcluido: "Cadastro não concluído",
  tituloDaAba: "Cadastro · Duora",
  rotuloDaData: "Data de nascimento",
  ajudaDaData: "Não dá para mudar depois.",
  /** O parágrafo dos links; os nomes dos documentos viram links fora do rótulo da caixa. */
  leiaAntes: "Leia os",
  termos: "Termos de uso",
  e: "e a",
  privacidade: "Política de privacidade",
  novaAba: "(abre em nova aba)",
  rotuloDoAceite: "Li e aceito os Termos de uso e a Política de privacidade",
  concluir: "Concluir cadastro",
  concluindo: "Concluindo…",
  semData: "Informe sua data de nascimento.",
  semAceite: "Marque a caixa para aceitar os termos.",
  menorDeIdade: "O Duora é só para maiores de 18 anos. Não criamos a conta.",
  voltarAoTerraco: VOLTAR_AO_TERRACO,
  carregandoTermos: "Carregando os termos…",
  falhaDosTermos: "Não conseguimos carregar os termos. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
  /** Reservas para quando o servidor não manda a mensagem dele (ele sempre manda). */
  dataInvalida: "Confira a data de nascimento.",
  termosDesatualizados: "Os termos mudaram. Leia a versão nova e marque a caixa de novo.",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar). */
  falha: "Não conseguimos concluir o cadastro. Tente de novo em instantes.",
} as const;

export const MINHA_CONTA = {
  titulo: "Minha conta",
  tituloDaAba: "Minha conta · Duora",
  /** Só com AGUARDANDO_VERIFICACAO, junto do link para a verificação. */
  faltaConfirmar: "Falta confirmar sua idade.",
  confirmarIdade: "Confirmar minha idade",
  /** Só com a verificação de idade aprovada. */
  selo: "Conta verificada",
  descricaoDoSelo: "Confirmamos que você tem 18 anos ou mais.",
  sair: "Sair da conta",
  saindo: "Saindo…",
  /** Falha sem resposta conclusiva (rede, servidor fora do ar): a sessão pode seguir aberta. */
  falhaAoSair: "Não conseguimos encerrar a sessão. Tente de novo em instantes.",
  tituloDaExclusao: "Excluir conta",
  avisoDaExclusao: "Apaga seus dados pessoais. Não dá para desfazer.",
  excluir: "Excluir conta",
  excluindo: "Excluindo…",
  tituloDoDialogo: "Excluir sua conta?",
  textoDoDialogo: "Apagamos seus dados pessoais agora. Não dá para desfazer.",
  /**
   * Falha sem resposta conclusiva. Tentar de novo é seguro: se a exclusão chegou ao servidor, a nova
   * tentativa recebe 401 e a tela leva a /entrar.
   */
  falhaAoExcluir: "Não conseguimos excluir a conta. Tente de novo em instantes.",
  tituloExcluida: "Conta excluída",
  tituloDaAbaExcluida: "Conta excluída · Duora",
  voltarAoTerraco: VOLTAR_AO_TERRACO,
} as const;

/** A casca de conta na primeira carga, enquanto a tela pedida chega. */
export const CASCA = {
  carregando: "Carregando…",
} as const;

/** Falha de qualquer rota da casca: a leitura da conta, o download da tela ou o desenho dela. */
export const ERRO_DA_CONTA = {
  tituloIndisponivel: "Conta indisponível",
  tituloDaAbaIndisponivel: "Conta indisponível · Duora",
  titulo: "Não conseguimos abrir esta página",
  tituloDaAba: "Página indisponível · Duora",
  /** Vale para a leitura da conta, o download da tela e erro ao desenhá-la. */
  falha: "Pode ter sido a conexão ou uma falha nossa. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
  voltarAoTerraco: VOLTAR_AO_TERRACO,
} as const;

/** Texto do 429: o Retry-After em segundos vira minutos inteiros, arredondados para cima, nunca menos de 1. */
export function textoDeEspera(segundos: number): string {
  const minutos = Math.max(1, Math.ceil(segundos / 60));
  return `Muitas tentativas. Tente de novo em ${String(minutos)} ${minutos === 1 ? "minuto" : "minutos"}.`;
}
