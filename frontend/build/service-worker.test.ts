// @vitest-environment node
import { existsSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { afterAll, beforeAll, describe, expect, it } from "vitest";
import { impressao } from "./impressao";
import { gerarServiceWorker } from "./service-worker";

// Um build falso: o manifest do Vite e os arquivos que ele cita, mais uma raiz estática à parte.
// O sw.ts compilado é o de verdade (src/sw/sw.ts).
const raizDoFront = join(import.meta.dirname, "..");
const pastas: string[] = [];

const MANIFEST = {
  "index.html": {
    file: "app/index-abc.js",
    src: "index.html",
    isEntry: true,
    imports: ["_polling-def.js"],
    css: ["app/index-abc.css"],
  },
  "_polling-def.js": { file: "app/polling-def.js", imports: ["_comum-jkl.js"], css: ["app/polling-def.css"] },
  "_comum-jkl.js": { file: "app/comum-jkl.js" },
  "src/tardio.ts": { file: "app/tardio-ghi.js", isDynamicEntry: true, imports: ["_comum-jkl.js"] },
};

function escrever(raiz: string, caminho: string, conteudo: string): void {
  const destino = join(raiz, caminho);
  mkdirSync(dirname(destino), { recursive: true });
  writeFileSync(destino, conteudo);
}

function montarBuild(
  manifest: object = MANIFEST,
  caminhoDoManifest = ".vite/manifest.json",
): { outDir: string; raizEstatica: string } {
  const pasta = mkdtempSync(join(tmpdir(), "duora-sw-"));
  pastas.push(pasta);
  const outDir = join(pasta, "saida");
  const raizEstatica = join(pasta, "estatica");
  escrever(outDir, caminhoDoManifest, JSON.stringify(manifest));
  escrever(outDir, "index.html", "<!doctype html>");
  for (const arquivo of [
    "app/index-abc.js",
    "app/index-abc.css",
    "app/polling-def.js",
    "app/polling-def.css",
    "app/comum-jkl.js",
    "app/tardio-ghi.js",
  ]) {
    escrever(outDir, arquivo, `/* ${arquivo} */`);
  }
  escrever(raizEstatica, "manifest.webmanifest", "{}");
  escrever(raizEstatica, "assets/fonts/fonte.woff2", "fonte");
  escrever(raizEstatica, "assets/.DS_Store", "lixo do Finder");
  return { outDir, raizEstatica };
}

afterAll(() => {
  for (const pasta of pastas) {
    rmSync(pasta, { recursive: true, force: true });
  }
});

describe("build com todos os arquivos do shell", () => {
  let build: { outDir: string; raizEstatica: string };
  let sw = "";
  let versaoEsperada = "";

  beforeAll(async () => {
    build = montarBuild();
    // A impressão cobre o que será servido: a saída (menos .vite/) e a raiz estática (menos ocultos).
    const servidos = [
      "index.html",
      "app/index-abc.js",
      "app/index-abc.css",
      "app/polling-def.js",
      "app/polling-def.css",
      "app/comum-jkl.js",
      "app/tardio-ghi.js",
    ].map((caminho) => ({ caminho, conteudo: readFileSync(join(build.outDir, caminho)) }));
    servidos.push(
      ...["manifest.webmanifest", "assets/fonts/fonte.woff2"].map((caminho) => ({
        caminho,
        conteudo: readFileSync(join(build.raizEstatica, caminho)),
      })),
    );
    versaoEsperada = `duora-${impressao(servidos)}`;

    await gerarServiceWorker({
      ...build,
      raizDoFront,
      assetsDoShell: ["/assets/fonts/fonte.woff2"],
    });
    sw = readFileSync(join(build.outDir, "sw.js"), "utf-8");
  }, 60_000);

  it("o sw.js começa com as duas linhas fixas", () => {
    expect(sw).toMatch(/^const CACHE_VERSION = "duora-[0-9a-f]{12}";\nconst SHELL = \[/);
  });

  it("o shell tem a entrada, os seus imports e css, e os assets pedidos", () => {
    const shell: unknown = JSON.parse(/^const SHELL = (\[.*\]);$/m.exec(sw)?.[1] ?? "null");

    expect(shell).toEqual([
      "/",
      "/index.html",
      "/manifest.webmanifest",
      "/app/index-abc.js",
      "/app/polling-def.js",
      "/app/comum-jkl.js",
      "/app/polling-def.css",
      "/app/index-abc.css",
      "/assets/fonts/fonte.woff2",
    ]);
  });

  it("a versão é a impressão dos arquivos servidos", () => {
    expect(sw).toContain(`const CACHE_VERSION = "${versaoEsperada}";`);
  });

  it("o código compilado é um script clássico", () => {
    expect(sw).toContain('addEventListener("fetch"');
    expect(sw).not.toMatch(/^\s*(import|export)\b/m);
  });

  it("a pasta .vite é apagada", () => {
    expect(existsSync(join(build.outDir, ".vite"))).toBe(false);
  });
});

it("um item do shell sem arquivo falha o build", async () => {
  const build = montarBuild();

  await expect(
    gerarServiceWorker({ ...build, raizDoFront, assetsDoShell: ["/assets/icones/sumido.png"] }),
  ).rejects.toThrow("/assets/icones/sumido.png");
  expect(existsSync(join(build.outDir, "sw.js"))).toBe(false);
});

// build.manifest pode ser um caminho próprio em vez de .vite/manifest.json; ele também não é público.
it("um manifest em caminho próprio é lido e apagado", async () => {
  const build = montarBuild(MANIFEST, "manifesto.json");

  await gerarServiceWorker({ ...build, raizDoFront, assetsDoShell: [], manifest: "manifesto.json" });

  expect(readFileSync(join(build.outDir, "sw.js"), "utf-8")).toContain('"/app/index-abc.js"');
  expect(existsSync(join(build.outDir, "manifesto.json"))).toBe(false);
});

it("um import que falta no manifest falha o build", async () => {
  const build = montarBuild({
    "index.html": { file: "app/index-abc.js", src: "index.html", isEntry: true, imports: ["_sumido-xyz.js"] },
  });

  await expect(gerarServiceWorker({ ...build, raizDoFront, assetsDoShell: [] })).rejects.toThrow(
    '"_sumido-xyz.js" não está no manifest',
  );
  expect(existsSync(join(build.outDir, "sw.js"))).toBe(false);
});
