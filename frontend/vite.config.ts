import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";
import { pluginServiceWorker } from "./build/service-worker.ts";

// Build: saída em ../target/vite, com os arquivos com hash em /app/ (sem colidir com /assets/).
// Dev: os binários de /assets/ vêm de src/main/resources/static e o /api vai para o Spring.
const RAIZ_ESTATICA = "../src/main/resources/static";

export default defineConfig(({ command }) => ({
  plugins: [
    react(),
    tailwindcss(),
    // Gera o sw.js do manifest do Vite e apaga .vite/ (docs/adr/0005-pwa-cache-por-impressao.md).
    pluginServiceWorker({
      raizEstatica: RAIZ_ESTATICA,
      assetsDoShell: [
        "/assets/fonts/atkinson-next-400.woff2",
        "/assets/fonts/atkinson-next-600.woff2",
        "/assets/fonts/fraunces-600.woff2",
        "/assets/icone.svg",
        "/assets/icones/icone-192.png",
        "/assets/icones/icone-512.png",
        "/assets/icones/maskable-192.png",
        "/assets/icones/maskable-512.png",
        "/assets/icones/apple-touch-180.png",
      ],
    }),
  ],
  publicDir: command === "serve" ? RAIZ_ESTATICA : false,
  build: {
    outDir: "../target/vite",
    emptyOutDir: true,
    assetsDir: "app",
    manifest: true,
    // Sem o polyfill de modulepreload: os navegadores-alvo já têm <link rel="modulepreload">, e ele
    // pesava no orçamento do JS inicial (meta de 80 KB em gzip, TamanhoDoJsInicialTest).
    modulePreload: { polyfill: false },
    rolldownOptions: {
      // Fontes e sprites de /assets/ são servidos pelo Spring (src/main/resources/static) e resolvidos
      // em tempo de execução; declarar como externos evita o aviso "didn't resolve at build time".
      external: [/^\/assets\//],
      output: {
        codeSplitting: {
          // O laço de polling num chunk próprio (app/polling-*.js): a sonda do PollingIT reconhece
          // os setTimeout dele pela pilha, e só um arquivo com nome fixo sobrevive ao bundle.
          groups: [{ name: "polling", test: /src\/api\/polling\.ts/ }],
        },
      },
    },
  },
  server: {
    // As fixtures do Vitest leem os JSON do cenário direto de RAIZ_ESTATICA (teste/cenarioDoTerraco.ts),
    // fora da raiz do Vite; o diretório já é público (publicDir no dev).
    fs: { allow: [".", RAIZ_ESTATICA] },
    proxy: {
      "/api": "http://127.0.0.1:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["src/teste/configurar.ts"],
    // O Vitest troca todo .css por "" (inclusive o ?raw); os testes que leem o CSS como texto
    // precisam do conteúdo, e o ?raw não passa pelo processamento de CSS do Vite.
    css: { include: [/\.css\?raw$/] },
  },
}));
