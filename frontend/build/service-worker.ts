import { existsSync, mkdtempSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, relative, resolve, sep } from "node:path";
import { build, type Manifest, type Plugin, type ResolvedConfig } from "vite";
import { impressao } from "./impressao.ts";

export interface OpcoesDoServiceWorker {
  /** Pasta servida junto da saída do Vite (os binários de /assets/), relativa à raiz do front. */
  raizEstatica: string;
  /** URLs fora do manifest que também entram no shell (fontes e ícones). */
  assetsDoShell: readonly string[];
}

export interface OpcoesDaGeracao {
  /** A raiz do front, onde está src/sw/sw.ts. */
  raizDoFront: string;
  /** A saída do build do app. */
  outDir: string;
  /** Caminho absoluto da pasta estática servida junto da saída. */
  raizEstatica: string;
  assetsDoShell: readonly string[];
  /** O manifest do Vite, relativo a outDir (o `build.manifest` quando é um caminho). */
  manifest?: string;
}

const PASTA_DO_VITE = ".vite";
const MANIFEST_PADRAO = `${PASTA_DO_VITE}/manifest.json`;
const SW = "sw.js";
const ENTRADA_DO_APP = "index.html";

/**
 * Gera o sw.js ao fim do build. Só age quando o build escreve o manifest: os testes que constroem o
 * app para olhar o CSS ou os avisos desligam o manifest, e aí não há shell para listar. O recado
 * sai como info, não como aviso: o teste de avisos exige um build sem avisos, e o Maven registra o
 * stderr como [ERROR].
 */
export function pluginServiceWorker(opcoes: OpcoesDoServiceWorker): Plugin {
  let config: ResolvedConfig | undefined;
  return {
    name: "duora:service-worker",
    apply: "build",
    configResolved(resolvida) {
      config = resolvida;
    },
    async closeBundle(erro) {
      // O closeBundle roda mesmo quando o build falha; aí a saída pode estar incompleta.
      if (erro !== undefined || config === undefined) {
        return;
      }
      const { manifest } = config.build;
      if (manifest === false) {
        config.logger.info("duora:service-worker: build sem manifest, sw.js não gerado");
        return;
      }
      await gerarServiceWorker({
        raizDoFront: config.root,
        outDir: resolve(config.root, config.build.outDir),
        raizEstatica: resolve(config.root, opcoes.raizEstatica),
        assetsDoShell: opcoes.assetsDoShell,
        manifest: manifest === true ? MANIFEST_PADRAO : manifest,
      });
    },
  };
}

/**
 * Lê o manifest do Vite, monta o SHELL, calcula a impressão do que será servido, compila
 * src/sw/sw.ts como script clássico e grava `<outDir>/sw.js`; por fim apaga o manifest e `.vite/`,
 * que não devem ser públicos. Um item do SHELL sem arquivo falha o build antes de gravar qualquer coisa.
 */
export async function gerarServiceWorker(opcoes: OpcoesDaGeracao): Promise<void> {
  const caminhoDoManifest = (opcoes.manifest ?? MANIFEST_PADRAO).split(sep).join("/");
  const arquivoDoManifest = join(opcoes.outDir, caminhoDoManifest);
  const manifest = JSON.parse(readFileSync(arquivoDoManifest, "utf-8")) as Manifest;
  const servidos = arquivosServidos(opcoes.outDir, opcoes.raizEstatica);
  servidos.delete(caminhoDoManifest);
  const shell = montarShell(manifest, opcoes.assetsDoShell);

  const ausentes = shell.filter((url) => !servidos.has(arquivoDaUrl(url)));
  if (ausentes.length > 0) {
    throw new Error(
      `Service worker: itens do SHELL sem arquivo em ${opcoes.outDir} nem em ${opcoes.raizEstatica}: ${ausentes.join(", ")}`,
    );
  }

  const versao = impressao([...servidos].map(([caminho, absoluto]) => ({ caminho, conteudo: readFileSync(absoluto) })));
  const codigo = await compilarWorker(opcoes.raizDoFront);
  writeFileSync(
    join(opcoes.outDir, SW),
    `const CACHE_VERSION = "duora-${versao}";\nconst SHELL = ${JSON.stringify(shell)};\n\n${codigo}`,
  );
  rmSync(arquivoDoManifest, { force: true });
  rmSync(join(opcoes.outDir, PASTA_DO_VITE), { recursive: true, force: true });
}

/** "/" e o HTML, o manifest do PWA, a entrada com os seus imports e css (em recursão) e os assets. */
function montarShell(manifest: Manifest, assetsDoShell: readonly string[]): string[] {
  const shell = new Set(["/", "/index.html", "/manifest.webmanifest"]);
  const visitados = new Set<string>();
  const visitar = (chave: string): void => {
    const pedaco = manifest[chave];
    if (pedaco === undefined) {
      throw new Error(`Service worker: "${chave}" não está no manifest do Vite`);
    }
    if (visitados.has(chave)) {
      return;
    }
    visitados.add(chave);
    shell.add(`/${pedaco.file}`);
    for (const importado of pedaco.imports ?? []) {
      visitar(importado);
    }
    for (const css of pedaco.css ?? []) {
      shell.add(`/${css}`);
    }
  };
  visitar(ENTRADA_DO_APP);
  for (const asset of assetsDoShell) {
    shell.add(asset);
  }
  return [...shell];
}

/** O servidor entrega o index.html na raiz. */
function arquivoDaUrl(url: string): string {
  return url === "/" ? "index.html" : url.slice(1);
}

/**
 * Caminho servido → arquivo no disco: a raiz estática e, por cima, a saída do Vite, sem o sw.js
 * (que guarda o resultado), sem .vite/ e sem ocultos como .DS_Store, como o ServiceWorkerVersaoTest.
 */
function arquivosServidos(outDir: string, raizEstatica: string): Map<string, string> {
  const servidos = new Map<string, string>();
  for (const raiz of [raizEstatica, outDir]) {
    for (const caminho of arquivosEm(raiz)) {
      if (caminho !== SW && !caminho.startsWith(`${PASTA_DO_VITE}/`)) {
        servidos.set(caminho, join(raiz, caminho));
      }
    }
  }
  return servidos;
}

function arquivosEm(raiz: string): string[] {
  return readdirSync(raiz, { recursive: true, withFileTypes: true })
    .filter((entrada) => entrada.isFile() && !entrada.name.startsWith("."))
    .map((entrada) => relative(raiz, join(entrada.parentPath, entrada.name)).split(sep).join("/"));
}

/**
 * Compila src/sw/sw.ts num build à parte: sem o vite.config.ts (senão este plugin rodaria de novo)
 * e numa pasta temporária (senão o emptyOutDir poderia limpar a saída do app).
 */
async function compilarWorker(raizDoFront: string): Promise<string> {
  const temporaria = mkdtempSync(join(tmpdir(), "duora-sw-build-"));
  try {
    await build({
      configFile: false,
      root: raizDoFront,
      logLevel: "silent",
      publicDir: false,
      build: {
        outDir: temporaria,
        emptyOutDir: false,
        minify: false,
        lib: {
          entry: join(raizDoFront, "src", "sw", "sw.ts"),
          formats: ["iife"],
          name: "duoraServiceWorker",
          fileName: () => SW,
        },
      },
    });
    const compilado = join(temporaria, SW);
    if (!existsSync(compilado)) {
      throw new Error(`Service worker: o build de src/sw/sw.ts não gerou ${SW}`);
    }
    return readFileSync(compilado, "utf-8");
  } finally {
    rmSync(temporaria, { recursive: true, force: true });
  }
}
