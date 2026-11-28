import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { AvisoDoDocumento, LadoDoDocumento } from "./etapas";
import { EnvioDeDocumento } from "./EnvioDeDocumento";
import { VERIFICACAO_DE_IDADE } from "./textos";

/** O jsdom não decodifica imagem: a redução devolve um blob conhecido e a prévia não desenha nada. */
const fotos = vi.hoisted(() => ({
  reduzirFoto: vi.fn<(arquivo: File) => Promise<Blob>>(),
  desenharPrevia: vi.fn<(canvas: HTMLCanvasElement, foto: Blob) => Promise<void>>(() => Promise.resolve()),
}));
vi.mock("./fotos", () => fotos);

const TEXTOS = VERIFICACAO_DE_IDADE.documento;
const FRENTE = new Blob(["frente"], { type: "image/jpeg" });
const VERSO = new Blob(["verso"], { type: "image/jpeg" });
const ARQUIVO = new File(["bytes"], "IMG_0001.jpg", { type: "image/jpeg" });

interface Opcoes {
  frente?: Blob;
  verso?: Blob;
  aviso?: AvisoDoDocumento;
  envios?: number;
}

function abrir({ frente, verso, aviso, envios = 2 }: Opcoes = {}) {
  const aoEscolher = vi.fn<(lado: LadoDoDocumento, foto: Blob | null) => void>();
  const aoFazerSelfie = vi.fn<() => void>();
  render(
    <EnvioDeDocumento
      {...(frente === undefined ? {} : { frente })}
      {...(verso === undefined ? {} : { verso })}
      {...(aviso === undefined ? {} : { aviso })}
      envios={envios}
      focar
      aoEscolher={aoEscolher}
      aoFazerSelfie={aoFazerSelfie}
    />,
  );
  return { aoEscolher, aoFazerSelfie };
}

function campo(rotulo: RegExp): HTMLInputElement {
  return screen.getByLabelText<HTMLInputElement>(rotulo);
}

afterEach(() => {
  fotos.reduzirFoto.mockReset();
  fotos.desenharPrevia.mockClear();
});

describe("EnvioDeDocumento", () => {
  it("tituloRecebeOFoco", () => {
    abrir();

    expect(screen.getByRole("heading", { level: 2, name: TEXTOS.titulo })).toHaveFocus();
  });

  it("mencionaADiditEOApagamento", () => {
    abrir();

    expect(screen.getByText(TEXTOS.texto)).toBeInTheDocument();
    expect(TEXTOS.texto).toContain("A Didit, empresa de verificação de identidade");
    expect(TEXTOS.texto).toContain("A Didit apaga as imagens logo depois");
  });

  it("inputsAceitamJpegPngComCameraTraseira", () => {
    abrir();

    for (const rotulo of [/Frente do documento/, /Verso do documento/]) {
      const entrada = campo(rotulo);
      expect(entrada).toHaveAttribute("type", "file");
      expect(entrada).toHaveAttribute("accept", "image/jpeg,image/png");
      expect(entrada).toHaveAttribute("capture", "environment");
      expect(entrada).toHaveAccessibleDescription(expect.stringContaining(TEXTOS.dica));
    }
  });

  it("fazerSelfieIndisponivelSemAsDuasFotos", async () => {
    const usuario = userEvent.setup();
    const { aoFazerSelfie } = abrir({ frente: FRENTE });

    const botao = screen.getByRole("button", { name: TEXTOS.fazerSelfie });
    await usuario.click(botao);

    expect(botao).toHaveAttribute("aria-disabled", "true");
    expect(botao).toHaveAccessibleDescription(TEXTOS.faltamFotos);
    expect(aoFazerSelfie).not.toHaveBeenCalled();
  });

  it("comAsDuasFotosFazASelfie", async () => {
    const usuario = userEvent.setup();
    const { aoFazerSelfie } = abrir({ frente: FRENTE, verso: VERSO });

    const botao = screen.getByRole("button", { name: TEXTOS.fazerSelfie });
    await usuario.click(botao);

    expect(botao).not.toHaveAttribute("aria-disabled");
    expect(botao).toHaveAccessibleDescription(TEXTOS.dicaDoEnvio);
    expect(aoFazerSelfie).toHaveBeenCalledTimes(1);
  });

  it("fotoEscolhidaSobeReduzida", async () => {
    const usuario = userEvent.setup();
    const reduzida = new Blob(["reduzida"], { type: "image/jpeg" });
    fotos.reduzirFoto.mockResolvedValue(reduzida);
    const { aoEscolher } = abrir();

    await usuario.upload(campo(/Frente do documento/), ARQUIVO);

    expect(fotos.reduzirFoto).toHaveBeenCalledWith(ARQUIVO);
    expect(aoEscolher).toHaveBeenCalledWith("frente", reduzida);
  });

  it("fotoEscolhidaTrocaOBotaoEMostraAPrevia", () => {
    const criarUrl = vi.spyOn(URL, "createObjectURL");
    abrir({ frente: FRENTE });

    expect(campo(/Frente do documento/)).toHaveAccessibleName(`Frente do documento ${TEXTOS.trocarFoto}`);
    expect(campo(/Verso do documento/)).toHaveAccessibleName(`Verso do documento ${TEXTOS.escolherFoto}`);
    expect(fotos.desenharPrevia).toHaveBeenCalledWith(expect.any(HTMLCanvasElement), FRENTE);
    expect(document.querySelector("canvas")).toHaveAttribute("aria-hidden", "true");
    expect(criarUrl).not.toHaveBeenCalled();
    criarUrl.mockRestore();
  });

  it("fotoIlegivelMostraErroNoCampo", async () => {
    const usuario = userEvent.setup();
    fotos.reduzirFoto.mockRejectedValue(new Error("FOTO_ILEGIVEL"));
    const { aoEscolher } = abrir({ frente: FRENTE });

    const entrada = campo(/Frente do documento/);
    await usuario.upload(entrada, ARQUIVO);

    expect(await screen.findByText(TEXTOS.fotoIlegivel)).toBeInTheDocument();
    expect(entrada).toHaveAttribute("aria-invalid", "true");
    expect(entrada).toHaveAccessibleDescription(expect.stringContaining(TEXTOS.fotoIlegivel));
    expect(aoEscolher).toHaveBeenCalledWith("frente", null);
  });

  it("fotoBoaDepoisDaIlegivelLimpaOErro", async () => {
    const usuario = userEvent.setup();
    fotos.reduzirFoto.mockRejectedValueOnce(new Error("FOTO_ILEGIVEL")).mockResolvedValueOnce(FRENTE);
    abrir();

    const entrada = campo(/Frente do documento/);
    await usuario.upload(entrada, ARQUIVO);
    await screen.findByText(TEXTOS.fotoIlegivel);
    await usuario.upload(entrada, ARQUIVO);

    expect(screen.queryByText(TEXTOS.fotoIlegivel)).not.toBeInTheDocument();
    expect(entrada).not.toHaveAttribute("aria-invalid");
  });

  it("naoLeuMostraOsEnviosRestantes", () => {
    abrir({ aviso: "naoLeu", envios: 1 });

    expect(screen.getByRole("alert")).toHaveTextContent(TEXTOS.naoLeu);
    expect(screen.getByText("Envios restantes hoje: 1")).toBeInTheDocument();
  });

  it.each<[AvisoDoDocumento, string]>([
    ["selfieVenceu", TEXTOS.selfieVenceu],
    ["fotosIlegiveis", TEXTOS.fotosIlegiveis],
    ["fotosGrandes", TEXTOS.fotosGrandes],
  ])("aviso %s vem como alerta", (aviso, texto) => {
    abrir({ aviso });

    expect(screen.getByRole("alert")).toHaveTextContent(texto);
  });
});
