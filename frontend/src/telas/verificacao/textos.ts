// Textos fixos da verificação de idade. A espera da primeira carga e a falha de leitura são da casca
// (CASCA e ERRO_DA_CONTA, em telas/conta/textos.ts).

import type { FalhaDoDetector } from "./etapas";

const CAMERA_NEGADA =
  "Não conseguimos usar a câmera. Libere o acesso nas configurações do navegador e toque em Tentar de novo.";
const SERVICO_FORA = "O serviço de verificação não respondeu. Tente de novo em instantes.";

/** Por falha do detector; cada uma vem com o "Tentar de novo" e as tentativas que sobram. */
const FALHAS: Record<FalhaDoDetector, string> = {
  camera: CAMERA_NEGADA,
  tempo: "O tempo da selfie acabou. Comece de novo quando puder.",
  rosto: "Não conseguimos ver seu rosto direito. Centralize o rosto, com boa luz, e tente de novo.",
  outraPessoa: "Só você pode aparecer na selfie. Peça para a outra pessoa sair do quadro.",
  conexao: "A conexão caiu durante a selfie. Confira a internet e tente de novo.",
  servico: SERVICO_FORA,
  aparelho: "Esta câmera ou este navegador não deu conta da selfie. Tente em outro aparelho.",
  orientacao: "Deixe o celular em pé e tente de novo.",
  carregamento: "Não conseguimos carregar a selfie. Confira a conexão e tente de novo.",
};

export const VERIFICACAO_DE_IDADE = {
  titulo: "Confirme sua idade",
  tituloDaAba: "Confirmar idade · Duora",
  paraQue: "O Duora é só para maiores de 18 anos. Para confirmar, pedimos uma selfie em vídeo de poucos segundos.",
  usoDaImagem:
    "A AWS, nos Estados Unidos, confere se há uma pessoa real diante da câmera e estima a idade. Usamos a imagem só para isso e a apagamos logo depois. Guardamos só o resultado, a data e o método.",
  dicas: ["Procure um lugar claro.", "Tire óculos escuros, boné ou máscara.", "Fique sem outra pessoa no quadro."],
  /** O aviso de luz em volta do e-mail do encarregado, que entra como link mailto. */
  avisoDeLuzAntes:
    "Durante a selfie, a tela pisca em cores. Se luzes piscando fazem mal a você, não comece: escreva para ",
  avisoDeLuzDepois: " e combinamos outro jeito de confirmar sua idade.",
  consentimento: "Concordo com o uso da minha imagem para confirmar minha idade",
  comecar: "Começar a selfie",
  semConsentimento: "Marque a caixa para começar.",
  semSuporte: "Este navegador não abre a câmera. Abra o Duora no Chrome, no Safari ou no Firefox atualizado.",
  selfieEmVideo: "Selfie em vídeo",
  abrindoCamera: "Abrindo a câmera…",
  preparando: "Preparando a selfie…",
  /** A espera do chunk do detector, que no 3G leva alguns segundos. */
  carregandoSelfie: "Carregando a selfie. Com internet lenta, pode demorar um pouco.",
  conferindo: "Conferindo a selfie…",
  cameraNegada: CAMERA_NEGADA,
  cameraAusente: "Não encontramos uma câmera neste aparelho. Use um celular ou computador com câmera.",
  cameraOcupada: "Outro app está usando a câmera. Feche esse app e tente de novo.",
  /** O mesmo texto do LIMITE_DE_VERIFICACAO do servidor. */
  limiteDoDia: "Você chegou ao limite de tentativas de hoje. Tente de novo amanhã.",
  servicoFora: SERVICO_FORA,
  tentarDeNovo: "Tentar de novo",
  voltarAoTerraco: "Voltar ao terraço",
  continuar: "Continuar",
  falhas: FALHAS,
  sessaoInvalida: "Esta selfie não vale mais. Comece de novo.",
  aprovada: {
    titulo: "Idade confirmada",
    texto: "Confirmamos que você tem 18 anos ou mais. Sua conta está ativa.",
  },
  repetir: {
    titulo: "Não deu para confirmar",
    texto: "Procure um lugar claro, tire óculos escuros e tente de novo.",
  },
  /** O texto da análise em volta do e-mail do encarregado, que entra como link mailto. */
  analise: {
    titulo: "Conta em análise",
    antes: "Precisamos de mais uma checagem. Se quiser contestar, escreva para ",
    depois: ".",
  },
  menor: {
    titulo: "Cadastro não concluído",
    texto: "O Duora é só para maiores de 18 anos. Encerramos o cadastro.",
  },
  /** O envio do documento (a tela completa vem com o envio das fotos). */
  documento: { titulo: "Falta pouco" },
  tentativas: (n: number): string => `Tentativas restantes hoje: ${String(n)}`,
} as const;
