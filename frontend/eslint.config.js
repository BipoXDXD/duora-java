import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

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
    },
  },
  {
    // O formato dos JSON do cenário é conhecido só por ler.ts; o resto do front usa o CenarioFront (R12).
    files: ["**/*.{ts,tsx}"],
    ignores: ["src/cenario/ler.ts"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          paths: [{ name: "valibot", message: "Só src/cenario/ler.ts importa o valibot; use o CenarioFront." }],
          patterns: [{ group: ["valibot/*"], message: "Só src/cenario/ler.ts importa o valibot; use o CenarioFront." }],
        },
      ],
    },
  },
  {
    files: ["**/*.js"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
]);
