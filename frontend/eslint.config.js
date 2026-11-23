import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

const MENSAGEM_DO_VALIBOT = "Só src/cenario/ler.ts importa o valibot; use o CenarioFront.";
const VALIBOT = { name: "valibot", message: MENSAGEM_DO_VALIBOT };
const VALIBOT_SUBMODULOS = { group: ["valibot/*"], message: MENSAGEM_DO_VALIBOT };
const MENSAGEM_DA_CASCA =
  "React Router e TanStack Query só em src/rotas e src/telas (casca de conta, carregada sob demanda).";
const CASCA = ["react-router", "react-router-dom"].map((name) => ({
  name,
  message: MENSAGEM_DA_CASCA,
}));
const CASCA_SUBMODULOS = { group: ["react-router/*", "@tanstack/*"], message: MENSAGEM_DA_CASCA };

export default defineConfig([
  // O frontend-maven-plugin instala o Node em node/; nada ali é código do projeto.
  globalIgnores(["node/", "node_modules/", "pnpm-lock.yaml"]),
  {
    files: ["**/*.{ts,tsx}"],
    extends: [js.configs.recommended, tseslint.configs.strictTypeChecked, reactHooks.configs.flat.recommended],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      "@typescript-eslint/switch-exhaustiveness-check": "error",
      // Avisos e confirmações são do próprio app, acessíveis; nunca a caixa do navegador.
      "no-alert": "error",
    },
  },
  {
    // O formato dos JSON do cenário é conhecido só por ler.ts; o resto do front usa o CenarioFront (R12).
    // React Router e TanStack Query ficam na casca de conta, fora do JS inicial do terraço.
    files: ["**/*.{ts,tsx}"],
    ignores: ["src/cenario/ler.ts", "src/rotas/**", "src/telas/**"],
    rules: {
      "no-restricted-imports": [
        "error",
        { paths: [VALIBOT, ...CASCA], patterns: [VALIBOT_SUBMODULOS, CASCA_SUBMODULOS] },
      ],
    },
  },
  {
    // Na casca vale só a regra do valibot.
    files: ["src/rotas/**/*.{ts,tsx}", "src/telas/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": ["error", { paths: [VALIBOT], patterns: [VALIBOT_SUBMODULOS] }],
    },
  },
  {
    files: ["**/*.js"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
]);
