// Os textos do FaceLivenessDetectorCore em pt-BR, com gênero neutro (vêm do SelfieCapture do C#). O
// `satisfies` obriga a cobrir toda chave do pacote: uma chave nova apareceria em inglês na tela. O
// pacote não exporta o LivenessDisplayText; a prop displayText do Core é ele. O import é só de tipo
// (some no build): o Amplify não entra no chunk de quem importa estes textos.

import type { FaceLivenessDetectorCoreProps } from "@aws-amplify/ui-react-liveness";

type LivenessDisplayText = NonNullable<FaceLivenessDetectorCoreProps["displayText"]>;

/**
 * As chaves antigas do aviso de luz, com o erro de digitação e marcadas como obsoletas: o pacote dá
 * preferência às novas e só lê as antigas quando as novas faltam (getDisplayText).
 */
type ChavesObsoletas = `photosensitivyWarning${"Body" | "Heading" | "Info" | "Label"}Text`;

/** Todas as chaves do displayText, menos as obsoletas. */
export type TextosDoDetector = Required<Omit<LivenessDisplayText, ChavesObsoletas>>;

const AVISO_DE_LUZ = {
  corpo: "Este procedimento pode exibir luzes coloridas. Cancele se causar desconforto.",
  titulo: "Sensibilidade à luz",
  informacao: "Informações sobre sensibilidade à luz",
  rotulo: "Aviso de sensibilidade à luz",
} as const;

export const TEXTOS_DO_DETECTOR = {
  startScreenBeginCheckText: "Iniciar captura",
  cancelLivenessCheckText: "Cancelar",
  hintCenterFaceText: "Centralize o rosto",
  hintCenterFaceInstructionText: "Mantenha o rosto inteiro visível e centralizado.",
  hintMoveFaceFrontOfCameraText: "Posicione o rosto diante da câmera",
  hintTooManyFacesText: "Só uma pessoa por vez",
  hintFaceDetectedText: "Rosto encontrado",
  hintCanNotIdentifyText: "Não foi possível encontrar o rosto",
  hintTooCloseText: "Afaste-se um pouco",
  hintTooFarText: "Aproxime-se um pouco",
  hintConnectingText: "Conectando…",
  hintVerifyingText: "Conferindo a selfie…",
  hintCheckCompleteText: "Captura concluída",
  hintIlluminationTooBrightText: "Reduza a iluminação direta",
  hintIlluminationTooDarkText: "Procure um local mais iluminado",
  hintIlluminationNormalText: "Iluminação adequada",
  hintHoldFaceForFreshnessText: "Mantenha a posição",
  hintFaceOffCenterText: "Centralize o rosto",
  hintMatchIndicatorText: "Siga a orientação na tela",
  cameraNotFoundHeadingText: "Câmera indisponível",
  cameraNotFoundMessageText: "Conecte uma câmera ou tente outro dispositivo.",
  cameraMinSpecificationsHeadingText: "Câmera incompatível",
  cameraMinSpecificationsMessageText: "Tente outro dispositivo compatível.",
  retryCameraPermissionsText: "Permitir câmera novamente",
  waitingCameraPermissionText: "Aguardando sua permissão para usar a câmera",
  a11yVideoLabelText: "Prévia da câmera para a selfie",
  recordingIndicatorText: "Capturando",
  goodFitCaptionText: "Posição adequada",
  goodFitAltText: "Rosto centralizado no enquadramento",
  tooFarCaptionText: "Muito distante",
  tooFarAltText: "Rosto distante do enquadramento",
  photosensitivityWarningBodyText: AVISO_DE_LUZ.corpo,
  photosensitivityWarningHeadingText: AVISO_DE_LUZ.titulo,
  photosensitivityWarningInfoText: AVISO_DE_LUZ.informacao,
  photosensitivityWarningLabelText: AVISO_DE_LUZ.rotulo,
  errorLabelText: "Não foi possível concluir",
  tryAgainText: "Tentar novamente",
  connectionTimeoutHeaderText: "Conexão interrompida",
  connectionTimeoutMessageText: "Verifique a conexão e tente novamente.",
  timeoutHeaderText: "Tempo esgotado",
  timeoutMessageText: "Inicie uma nova captura.",
  faceDistanceHeaderText: "Ajuste a posição",
  faceDistanceMessageText: "Centralize o rosto e siga as orientações.",
  multipleFacesHeaderText: "Mais de um rosto",
  multipleFacesMessageText: "Fique sem outra pessoa no quadro.",
  clientHeaderText: "Captura indisponível",
  clientMessageText: "Verifique a permissão de câmera ou tente outro dispositivo.",
  serverHeaderText: "Serviço indisponível",
  serverMessageText: "Tente novamente mais tarde.",
  landscapeHeaderText: "Ajuste a orientação",
  landscapeMessageText: "Mantenha o dispositivo na orientação indicada.",
  portraitMessageText: "Mantenha o dispositivo na vertical.",
} as const satisfies TextosDoDetector;
