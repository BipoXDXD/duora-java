// O que a selfie precisa do navegador: contexto seguro, câmera pelo getUserMedia e WebAssembly (o
// detector da AWS roda em wasm). A checagem abre a câmera só com vídeo, sem microfone, e a fecha logo
// depois: serve para pedir a permissão antes de gastar uma tentativa no servidor.

export type ResultadoDaCamera = "ok" | "negada" | "ausente" | "ocupada";
export type MotivoSemCamera = Exclude<ResultadoDaCamera, "ok">;

/** As peças da janela que a checagem de suporte lê; os testes montam a sua. */
export interface JanelaDaSelfie {
  isSecureContext: boolean;
  navigator: { mediaDevices?: { getUserMedia?: unknown } };
  WebAssembly?: unknown;
}

/** O nome do DOMException do getUserMedia; o que não está aqui vira "negada" (pede para liberar e tentar de novo). */
const MOTIVO_DO_ERRO = new Map<string, MotivoSemCamera>([
  ["NotAllowedError", "negada"],
  ["SecurityError", "negada"],
  ["NotFoundError", "ausente"],
  ["OverconstrainedError", "ausente"],
  ["NotReadableError", "ocupada"],
]);

export function navegadorTemSuporte(janela: JanelaDaSelfie = window): boolean {
  return (
    janela.isSecureContext &&
    typeof janela.navigator.mediaDevices?.getUserMedia === "function" &&
    typeof janela.WebAssembly === "object"
  );
}

function nomeDoErro(erro: unknown): string {
  return typeof erro === "object" && erro !== null && "name" in erro && typeof erro.name === "string" ? erro.name : "";
}

/** Pede a câmera (só vídeo) e para as trilhas na hora; a falha vira o motivo para a pessoa. */
export async function checarCamera(
  midia: Pick<MediaDevices, "getUserMedia"> = navigator.mediaDevices,
): Promise<ResultadoDaCamera> {
  let fluxo: MediaStream;
  try {
    fluxo = await midia.getUserMedia({ video: true, audio: false });
  } catch (erro) {
    return MOTIVO_DO_ERRO.get(nomeDoErro(erro)) ?? "negada";
  }
  for (const trilha of fluxo.getTracks()) {
    trilha.stop();
  }
  return "ok";
}
