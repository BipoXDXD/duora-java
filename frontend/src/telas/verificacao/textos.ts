// Textos fixos da verificação de idade. A espera da primeira carga e a falha de leitura são da casca
// (CASCA e ERRO_DA_CONTA, em telas/conta/textos.ts).

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
  cameraNegada:
    "Não conseguimos usar a câmera. Libere o acesso nas configurações do navegador e toque em Tentar de novo.",
  cameraAusente: "Não encontramos uma câmera neste aparelho. Use um celular ou computador com câmera.",
  cameraOcupada: "Outro app está usando a câmera. Feche esse app e tente de novo.",
  /** O mesmo texto do LIMITE_DE_VERIFICACAO do servidor. */
  limiteDoDia: "Você chegou ao limite de tentativas de hoje. Tente de novo amanhã.",
  servicoFora: "O serviço de verificação não respondeu. Tente de novo em instantes.",
  tentarDeNovo: "Tentar de novo",
  voltarAoTerraco: "Voltar ao terraço",
  tentativas: (n: number): string => `Tentativas restantes hoje: ${String(n)}`,
} as const;
