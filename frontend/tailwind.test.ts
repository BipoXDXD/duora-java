// @vitest-environment node
import { existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { build } from "vite";
import { afterAll, beforeAll, expect, it } from "vitest";

// A varredura de classes do Tailwind não pode depender do .gitignore (num build sem Git, como num
// contexto Docker, ele não vale). Uma isca fica em build/, fora de src/ e fora do .gitignore;
// a outra fica em src/, para provar que a varredura não foi simplesmente desligada.
const raiz = import.meta.dirname;
const pastaDeFora = join(raiz, "build");
const pastaDeForaJaExistia = existsSync(pastaDeFora);
const iscaDeFora = join(pastaDeFora, "isca-tailwind.html");
const iscaDeSrc = join(raiz, "src", "isca-tailwind.html");
const saida = mkdtempSync(join(tmpdir(), "duora-tailwind-"));
let css = "";

beforeAll(async () => {
  mkdirSync(pastaDeFora, { recursive: true });
  writeFileSync(iscaDeFora, '<div class="bg-[#123456]"></div>\n');
  writeFileSync(iscaDeSrc, '<div class="bg-[#654321]"></div>\n');

  await build({ root: raiz, logLevel: "silent", build: { outDir: saida, emptyOutDir: true, manifest: false } });

  const pastaApp = join(saida, "app");
  css = readdirSync(pastaApp)
    .filter((arquivo) => arquivo.endsWith(".css"))
    .map((arquivo) => readFileSync(join(pastaApp, arquivo), "utf-8"))
    .join("\n");
}, 60_000);

afterAll(() => {
  rmSync(pastaDeForaJaExistia ? iscaDeFora : pastaDeFora, { recursive: true, force: true });
  rmSync(iscaDeSrc, { force: true });
  rmSync(saida, { recursive: true, force: true });
});

it("o CSS gerado inclui classes de src/", () => {
  expect(css).toContain("654321");
});

it("o CSS gerado ignora classes de fora de src/", () => {
  expect(css).not.toContain("123456");
});
