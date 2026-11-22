/** Tela de toque sem hover (celular e tablet); falso onde não há matchMedia. */
export function ehToque(): boolean {
  if (typeof globalThis.matchMedia !== "function") {
    return false;
  }
  return globalThis.matchMedia("(hover: none) and (pointer: coarse)").matches;
}
