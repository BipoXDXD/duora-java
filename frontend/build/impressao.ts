import { createHash } from "node:crypto";

export interface ArquivoServido {
  /** Caminho relativo à raiz servida, com "/". */
  caminho: string;
  conteudo: Uint8Array;
}

/**
 * Impressão digital dos arquivos servidos: SHA-256 em ordem de caminho, com o caminho em UTF-8, um
 * byte zero e o conteúdo de cada arquivo; devolve os 12 primeiros hex. É o mesmo algoritmo do
 * ServiceWorkerVersaoTest (a ordem padrão do sort, por unidade UTF-16, é a do String.compareTo).
 */
export function impressao(arquivos: readonly ArquivoServido[]): string {
  const sha = createHash("sha256");
  const emOrdem = [...arquivos].sort((a, b) => (a.caminho < b.caminho ? -1 : a.caminho > b.caminho ? 1 : 0));
  for (const { caminho, conteudo } of emOrdem) {
    sha.update(caminho, "utf8");
    sha.update(new Uint8Array([0]));
    sha.update(conteudo);
  }
  return sha.digest("hex").slice(0, 12);
}
