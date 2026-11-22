// @vitest-environment node
import { cpSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync, symlinkSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { build } from "vite";
import { afterAll, beforeAll, expect, it } from "vitest";

// A varredura de classes do Tailwind não pode depender do .gitignore (num build sem Git, como num
// contexto Docker, ele não vale). O build roda numa cópia do front fora do repositório, onde não há
// .gitignore nenhum: uma isca fica em build/, fora de src/; a outra fica em src/, para provar que a
// varredura não foi simplesmente desligada. As iscas nunca tocam o repositório, então um processo
// morto antes do afterAll não deixa lixo para o Git.
const raiz = import.meta.dirname;
const temporaria = mkdtempSync(join(tmpdir(), "duora-tailwind-"));
const copia = join(temporaria, "front");
const saida = join(temporaria, "saida");
let css = "";

beforeAll(async () => {
  mkdirSync(copia);
  for (const item of ["index.html", "package.json", "src"]) {
    cpSync(join(raiz, item), join(copia, item), { recursive: true });
  }
  symlinkSync(join(raiz, "node_modules"), join(copia, "node_modules"), "dir");
  mkdirSync(join(copia, "build"));
  writeFileSync(join(copia, "build", "isca-tailwind.html"), '<div class="bg-[#123456]"></div>\n');
  writeFileSync(join(copia, "src", "isca-tailwind.html"), '<div class="bg-[#654321]"></div>\n');
  // O esqueleto do index.html pinta antes do JS: uma classe só dele precisa entrar no CSS.
  const index = join(copia, "index.html");
  writeFileSync(index, readFileSync(index, "utf-8").replace('class="cena"', 'class="cena bg-[#abcdef]"'));

  await build({
    root: copia,
    configFile: join(raiz, "vite.config.ts"),
    logLevel: "silent",
    build: { outDir: saida, emptyOutDir: true, manifest: false },
  });

  const pastaApp = join(saida, "app");
  css = readdirSync(pastaApp)
    .filter((arquivo) => arquivo.endsWith(".css"))
    .map((arquivo) => readFileSync(join(pastaApp, arquivo), "utf-8"))
    .join("\n");
}, 60_000);

afterAll(() => {
  rmSync(temporaria, { recursive: true, force: true });
});

it("o CSS gerado inclui classes de src/", () => {
  expect(css).toContain("654321");
});

it("o CSS gerado inclui classes do esqueleto do index.html", () => {
  expect(css).toContain("abcdef");
});

it("o CSS gerado ignora classes de fora de src/", () => {
  expect(css).not.toContain("123456");
});
