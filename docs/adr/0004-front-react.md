# 0004 — Front em React com build pelo Maven

- **Estado:** aceita
- **Data:** 2026-11-22
- **Etapa:** 1a

## Contexto

O terraço "Antes da primeira estrela" nasceu como JavaScript nativo em `src/main/resources/static/`: módulos ES sem build, um `main.js` com uma função `iniciar` de ~550 linhas (Long Function, Divergent Change) e CSS escrito à mão num arquivo de ~1 100 linhas. Isso bastava para uma tela só, mas a Etapa 2 traz cadastro, login e várias telas novas, e o código atual não tem tipos, não tem testes de unidade no front e mistura estado, DOM e regras de apresentação no mesmo lugar.

A migração não pode mudar o que a pessoa vê nem o que os testes medem: textos, ganchos de DOM (classes, ids, atributos `data-*`), a contagem de anúncios do `#aviso`, as contagens de `setTimeout` do polling e a CSP sem `unsafe-inline` continuam iguais.

## Decisão

- **Stack:** Vite + React 19 + TypeScript `strict` (com `noUncheckedIndexedAccess` e `verbatimModuleSyntax`) + Tailwind CSS 4, num projeto em `frontend/`.
- **Tailwind sem preflight:** só `tailwindcss/theme.css` e `tailwindcss/utilities.css` em camadas; o reset atual fica em `estilos/base.css`, para o visual não mudar. Os tokens de cor e fonte continuam com os mesmos nomes (`estilos/tokens.css`) e alimentam o `@theme inline`.
- **Build pelo Maven:** o `frontend-maven-plugin` (2.0.2) baixa Node e pnpm com versões fixas, e roda `pnpm install --frozen-lockfile`, o lint (ESLint com `--max-warnings 0` e Prettier), o build e o Vitest dentro do ciclo do `./mvnw verify`. O Vite escreve em `target/vite/` (arquivos com hash em `/app/`), que o `pom.xml` passa a declarar como recurso estático no corte (Task 7). Nada de build entra no Git.
- **Partes imperativas:** a cena (sprites, céu, meteoros, som), o `#aviso` e o laço de polling continuam imperativos, presos a elementos por ref ou num módulo próprio. O `#aviso` precisa gerar uma mutação de DOM a cada anúncio, mesmo repetido, e o React não gera mutação quando o texto não muda (R5). O polling fica num chunk próprio para a sonda do `PollingIT` achar o laço pela pilha (R4).
- **Router e Query adiados:** React Router e TanStack Query entram só na Etapa 2, com as primeiras rotas carregadas sob demanda (R1). O terraço tem uma tela só, e o agendamento do TanStack Query mudaria as contagens exatas que o `PollingIT` mede.
- **Versão do plugin (desvio do plano):** o plano fixava o `frontend-maven-plugin` 1.15.1, mas ele não instala o pnpm 11 ou mais novo. Desde o 11, o pacote do pnpm traz `bin/pnpm.mjs` e um binário nativo no lugar do `bin/pnpm.cjs`, e a 1.15.1 falha com "Could not link to pnpm executable". O suporte entrou na 2.0.1 ("Use pnpm .mjs file if it exists to support 11+"). Ficou a 2.0.2, a mais nova da linha 2.0 com mais de 14 dias de publicada, para manter o pnpm 12 do plano. A alternativa era voltar para o pnpm 10 e manter a 1.15.1.
- **Meta de tamanho:** JS inicial de até 80 KB gzip. Entre 80 e 90 KB, registra-se o valor e segue; acima de 90 KB, avalia-se o `preact/compat` com o dono.

## Consequências

- O front ganha tipos, lint tipado (`strictTypeChecked`, `switch-exhaustiveness-check`) e testes de unidade com Vitest e Testing Library, todos rodando no mesmo `./mvnw verify` da CI.
- O build fica mais longo e passa a depender do registro do npm e do download do Node na primeira execução; a CI guarda `frontend/node` e o store do pnpm em cache.
- React e ReactDOM sozinhos já ocupam a maior parte da meta de 80 KB (cerca de 69 KB gzip no esqueleto). Sobra pouco para o código do terraço, e a medida da Task 7 decide se a meta fecha.
- A cena e o aviso imperativos dentro de componentes React pedem cuidado: o React não pode controlar os nós que esse código altera.
- Quem desenvolve o front usa o Node e o pnpm baixados pelo plugin (`frontend/node`), não os globais da máquina.

## Alternativas consideradas

- **Manter JavaScript nativo:** zero dependências e nenhum build, mas sem tipos, sem testes de componente e com o `main.js` crescendo a cada tela da Etapa 2. Descartada porque o custo de manutenção sobe justamente quando entram as telas novas.
- **Preact (com `preact/compat`):** cerca de 4 KB gzip em vez de ~60 KB e quase a mesma API. Fica como plano B se o JS inicial passar de 90 KB; não é a escolha inicial porque parte do ecossistema (Testing Library, React Router, TanStack Query) é testada primeiro contra o React.
- **Svelte:** bundles pequenos e reatividade compilada, mas exige outra linguagem de template e outro conjunto de bibliotecas, sem reaproveitar o que a Etapa 2 planeja (React Router e TanStack Query).
