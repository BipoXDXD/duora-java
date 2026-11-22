import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Build: saída em ../target/vite, com os arquivos com hash em /app/ (sem colidir com /assets/).
// Dev: os binários de /assets/ vêm de src/main/resources/static e o /api vai para o Spring.
export default defineConfig(({ command }) => ({
  plugins: [react(), tailwindcss()],
  publicDir: command === "serve" ? "../src/main/resources/static" : false,
  build: {
    outDir: "../target/vite",
    emptyOutDir: true,
    assetsDir: "app",
    manifest: true,
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
