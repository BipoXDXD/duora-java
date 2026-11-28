// As fotos do documento, só em memória: decodifica com a orientação do EXIF, reduz e regrava em JPEG.
// A regravação descarta o EXIF (GPS, aparelho). Nada vira URL blob:, e cada bitmap é fechado.

/** O lado maior da prévia: basta para conferir se a foto saiu nítida, sem guardar outra cópia grande. */
const LADO_DA_PREVIA = 640;

function ilegivel(): Error {
  return new Error("FOTO_ILEGIVEL");
}

async function decodificar(foto: Blob): Promise<ImageBitmap> {
  try {
    return await createImageBitmap(foto, { imageOrientation: "from-image" });
  } catch {
    throw ilegivel();
  }
}

/** Desenha o bitmap inteiro num canvas com o lado maior limitado a `ladoMaximo`. */
function desenhar(canvas: HTMLCanvasElement, bitmap: ImageBitmap, ladoMaximo: number): void {
  const escala = Math.min(1, ladoMaximo / Math.max(bitmap.width, bitmap.height));
  canvas.width = Math.round(bitmap.width * escala);
  canvas.height = Math.round(bitmap.height * escala);
  const contexto = canvas.getContext("2d");
  if (contexto === null) {
    throw ilegivel();
  }
  contexto.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
}

/**
 * A foto escolhida, pronta para o envio: lado maior em até `ladoMaximo` px, JPEG com a `qualidade`
 * pedida. Uma foto que o navegador não abre (formato estranho, arquivo corrompido) rejeita com
 * `Error("FOTO_ILEGIVEL")`.
 */
export async function reduzirFoto(arquivo: File, ladoMaximo = 2000, qualidade = 0.85): Promise<Blob> {
  const bitmap = await decodificar(arquivo);
  try {
    const canvas = document.createElement("canvas");
    desenhar(canvas, bitmap, ladoMaximo);
    return await new Promise<Blob>((resolver, rejeitar) => {
      canvas.toBlob(
        (foto) => {
          if (foto === null) {
            rejeitar(ilegivel());
          } else {
            resolver(foto);
          }
        },
        "image/jpeg",
        qualidade,
      );
    });
  } finally {
    bitmap.close();
  }
}

/** A prévia da foto reduzida, desenhada direto no canvas da tela (sem URL blob: para revogar). */
export async function desenharPrevia(canvas: HTMLCanvasElement, foto: Blob): Promise<void> {
  const bitmap = await decodificar(foto);
  try {
    desenhar(canvas, bitmap, LADO_DA_PREVIA);
  } finally {
    bitmap.close();
  }
}
