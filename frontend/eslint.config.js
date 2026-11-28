import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

const MENSAGEM_DO_VALIBOT =
  "Só src/cenario/ler.ts (cenário) e src/api/perfil.ts (respostas do perfil) importam o valibot.";
const VALIBOT = { name: "valibot", message: MENSAGEM_DO_VALIBOT };
const VALIBOT_SUBMODULOS = { group: ["valibot/*"], message: MENSAGEM_DO_VALIBOT };
const MENSAGEM_DA_CASCA =
  "React Router e TanStack Query só em src/rotas e src/telas (casca de conta, carregada sob demanda).";
const CASCA = ["react-router", "react-router-dom"].map((name) => ({
  name,
  message: MENSAGEM_DA_CASCA,
}));
const CASCA_SUBMODULOS = { group: ["react-router/*", "@tanstack/*"], message: MENSAGEM_DA_CASCA };
const DETECTOR_AMPLIFY = "src/telas/verificacao/DetectorAmplify.tsx";
const MENSAGEM_DO_AMPLIFY = "Amplify só em src/telas/verificacao/DetectorAmplify.tsx (carregado sob demanda)";
// Import de tipo some no build e não puxa o Amplify para o chunk (os textos do detector usam o tipo).
const AMPLIFY = {
  group: ["aws-amplify", "aws-amplify/*", "@aws-amplify/*"],
  message: MENSAGEM_DO_AMPLIFY,
  allowTypeImports: true,
};

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
      // Parâmetro com _ é parte da assinatura de um contrato e não tem uso (ex.: o fetch falso que
      // ignora a URL, o caso nomeado de um it.each).
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      // Avisos e confirmações são do próprio app, acessíveis; nunca a caixa do navegador.
      "no-alert": "error",
    },
  },
  {
    // O formato dos JSON do cenário é conhecido só por ler.ts; o resto do front usa o CenarioFront (R12).
    // React Router e TanStack Query ficam na casca de conta, fora do JS inicial do terraço.
    files: ["**/*.{ts,tsx}"],
    // api/perfil.ts lê as respostas do perfil com valibot (plano da Etapa 3); não importa a casca.
    ignores: ["src/cenario/ler.ts", "src/api/perfil.ts", "src/rotas/**", "src/telas/**"],
    rules: {
      "no-restricted-imports": [
        "error",
        { paths: [VALIBOT, ...CASCA], patterns: [VALIBOT_SUBMODULOS, CASCA_SUBMODULOS] },
      ],
    },
  },
  {
    // api/perfil.ts pode o valibot, mas segue fora da casca: só a regra do React Router e do TanStack.
    files: ["src/api/perfil.ts"],
    rules: {
      "no-restricted-imports": ["error", { paths: CASCA, patterns: [CASCA_SUBMODULOS] }],
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
    // O Amplify (vários MB) só no detector, que o CapturaDeVida carrega por import(): fora do JS
    // inicial e do chunk da rota (ChunksDoFrontTest). A versão com tipos da regra deixa passar o
    // import de tipo.
    files: ["**/*.{ts,tsx}"],
    ignores: [DETECTOR_AMPLIFY],
    rules: {
      "@typescript-eslint/no-restricted-imports": ["error", { patterns: [AMPLIFY] }],
    },
  },
  {
    // Só os loaders das rotas redirecionam, e o React Router faz isso lançando a Response de redirect().
    files: ["src/rotas/**/*.{ts,tsx}"],
    rules: {
      "@typescript-eslint/only-throw-error": ["error", { allow: [{ from: "lib", name: "Response" }] }],
    },
  },
  {
    files: ["**/*.js"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
]);
