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
    rolldownOptions: {
      // Fontes e sprites de /assets/ são servidos pelo Spring (src/main/resources/static) e resolvidos
      // em tempo de execução; declarar como externos evita o aviso "didn't resolve at build time".
      external: [/^\/assets\//],
    },
  },
  server: {
    proxy: {
      "/api": "http://127.0.0.1:8080",
    },
  },
  test: {
    environment: "jsdom",
    setupFiles: ["src/teste/configurar.ts"],
  },
}));
