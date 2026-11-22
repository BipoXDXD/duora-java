// @vitest-environment node
import { mkdtempSync, readdirSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { build, createLogger } from "vite";
import { afterAll, beforeAll, expect, it } from "vitest";

// O Maven registra como [ERROR] tudo o que o build escreve no stderr; um build verde não deve avisar nada.
// Os binários de /assets/ (fontes, sprites) são servidos pelo Spring, não pelo Vite.
const raiz = import.meta.dirname;
const saida = mkdtempSync(join(tmpdir(), "duora-avisos-"));
const avisos: string[] = [];
let css = "";

beforeAll(async () => {
  const logger = createLogger("warn");
  logger.info = () => undefined;
  logger.warn = (mensagem) => avisos.push(mensagem);
  logger.warnOnce = (mensagem) => avisos.push(mensagem);
  logger.error = (mensagem) => avisos.push(mensagem);

  await build({ root: raiz, customLogger: logger, build: { outDir: saida, emptyOutDir: true, manifest: false } });

  const pastaApp = join(saida, "app");
  css = readdirSync(pastaApp)
    .filter((arquivo) => arquivo.endsWith(".css"))
    .map((arquivo) => readFileSync(join(pastaApp, arquivo), "utf-8"))
    .join("\n");
}, 60_000);

afterAll(() => {
  rmSync(saida, { recursive: true, force: true });
});

it("o build não emite avisos", () => {
  expect(avisos).toEqual([]);
});

it("as fontes continuam apontando para /assets/fonts", () => {
  expect(css).toContain("url(/assets/fonts/atkinson-next-400.woff2)");
});
