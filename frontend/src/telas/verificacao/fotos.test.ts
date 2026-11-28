import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from "vitest";
import { desenharPrevia, reduzirFoto } from "./fotos";

/** O jsdom não decodifica imagem nem desenha: o bitmap e o canvas são falsos, e o teste lê o que pediram a eles. */
function bitmap(width: number, height: number) {
  return { width, height, close: vi.fn() };
}

const desenho = { drawImage: vi.fn() };
/** Só o contexto 2D interessa: o tipo esquece as sobrecargas (webgl, bitmaprenderer) do getContext. */
let getContext: MockInstance<(this: HTMLCanvasElement, id: string) => unknown>;
let toBlob: MockInstance<HTMLCanvasElement["toBlob"]>;

/** O canvas em que a redução desenhou: o `this` da última chamada a getContext. */
function ultimoCanvas(): HTMLCanvasElement | undefined {
  return getContext.mock.contexts.at(-1);
}

beforeEach(() => {
  desenho.drawImage.mockReset();
  const prototipo: { getContext(this: HTMLCanvasElement, id: string): unknown } = HTMLCanvasElement.prototype;
  getContext = vi.spyOn(prototipo, "getContext");
  getContext.mockReturnValue(desenho);
  toBlob = vi
    .spyOn(HTMLCanvasElement.prototype, "toBlob")
    .mockImplementation((retorno: BlobCallback, tipo?: string) => {
      retorno(new Blob(["jpeg"], { type: tipo }));
    });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.unstubAllGlobals();
});

const ARQUIVO = new File(["bytes"], "IMG_0001.HEIC.jpg", { type: "image/jpeg" });

describe("reduzirFoto", () => {
  it.each([
    [4000, 3000, 2000, 1500],
    [1200, 800, 1200, 800],
    [3000, 4000, 1500, 2000],
  ])("ladoMaiorFicaEm2000 (%i×%i → %i×%i)", async (largura, altura, larguraFinal, alturaFinal) => {
    const original = bitmap(largura, altura);
    vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue(original));

    await reduzirFoto(ARQUIVO);

    expect(ultimoCanvas()?.width).toBe(larguraFinal);
    expect(ultimoCanvas()?.height).toBe(alturaFinal);
    expect(desenho.drawImage).toHaveBeenCalledWith(original, 0, 0, larguraFinal, alturaFinal);
    expect(original.close).toHaveBeenCalled();
  });

  it("saiJpegComQualidade085", async () => {
    vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue(bitmap(800, 600)));

    const foto = await reduzirFoto(ARQUIVO);

    expect(toBlob).toHaveBeenCalledWith(expect.any(Function), "image/jpeg", 0.85);
    expect(foto.type).toBe("image/jpeg");
  });

  it("pedeAOrientacaoDoExif", async () => {
    const criar = vi.fn().mockResolvedValue(bitmap(800, 600));
    vi.stubGlobal("createImageBitmap", criar);

    await reduzirFoto(ARQUIVO);

    expect(criar).toHaveBeenCalledWith(ARQUIVO, { imageOrientation: "from-image" });
  });

  it("fotoIlegivelRejeita", async () => {
    vi.stubGlobal("createImageBitmap", vi.fn().mockRejectedValue(new DOMException("ruim", "InvalidStateError")));

    await expect(reduzirFoto(ARQUIVO)).rejects.toThrow("FOTO_ILEGIVEL");
  });

  it("previaDesistidaNaoDesenha", async () => {
    const original = bitmap(800, 600);
    vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue(original));
    const desistencia = new AbortController();
    const canvas = document.createElement("canvas");

    const previa = desenharPrevia(canvas, new Blob(["jpeg"]), desistencia.signal);
    desistencia.abort();
    await previa;

    expect(desenho.drawImage).not.toHaveBeenCalled();
    expect(original.close).toHaveBeenCalled();
  });

  it("canvasSemImagemRejeita", async () => {
    const original = bitmap(800, 600);
    vi.stubGlobal("createImageBitmap", vi.fn().mockResolvedValue(original));
    toBlob.mockImplementation((retorno: BlobCallback) => {
      retorno(null);
    });

    await expect(reduzirFoto(ARQUIVO)).rejects.toThrow("FOTO_ILEGIVEL");
    expect(original.close).toHaveBeenCalled();
  });
});
