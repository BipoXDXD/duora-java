# 0005 — PWA com cache versionado pela impressão

- **Estado:** aceita
- **Data:** 2026-11-22
- **Etapa:** 1a

## Contexto

Na Etapa 1, o `sw.js` era escrito à mão em `src/main/resources/static/`: a lista `SHELL` citava cada módulo de `js/`, e o `CACHE_VERSION` era a impressão digital dos arquivos de `static/`, conferida pelo `ServiceWorkerVersaoTest` (mudou um arquivo, o teste falha e mostra o valor novo).

Com o Vite, os arquivos do app ganham hash no nome (`/app/index-<hash>.js`) e mudam a cada build. Uma lista escrita à mão ficaria velha no primeiro commit, e o valor da impressão não pode mais ser colado no código, porque depende do que o build gera.

## Decisão

- **Shell pelo manifest do Vite.** O plugin `pluginServiceWorker` (`frontend/build/service-worker.ts`) roda no `closeBundle` e lê `.vite/manifest.json`. O `SHELL` tem `/`, `/index.html`, `/manifest.webmanifest`, o `file` da entrada `index.html` com os seus `imports` (em recursão) e `css`, e os assets fixos passados no `vite.config.ts` (as três fontes, o `icone.svg` e os cinco ícones). Os imports dinâmicos ficam fora: vêm da rede na primeira vez e entram no cache. Um item do `SHELL` sem arquivo falha o build, porque um único item faltando quebra o `install` inteiro.
- **`CACHE_VERSION` pela impressão SHA-256.** É o mesmo algoritmo do `ServiceWorkerVersaoTest`: em ordem de caminho, o caminho em UTF-8, um byte zero e o conteúdo de cada arquivo servido (a saída do Vite e `src/main/resources/static`, menos o `sw.js`, a pasta `.vite/` e os ocultos); valem os 12 primeiros hex. O teste Java continua sendo a guarda: ele recalcula sobre `target/classes/static` e compara com o `sw.js` gerado.
- **`sw.js` como script clássico.** `src/sw/sw.ts` é compilado num build à parte (`configFile: false`, pasta temporária, IIFE sem minificar) e o `sw.js` começa com duas linhas fixas, `const CACHE_VERSION = "duora-<12 hex>";` e `const SHELL = [...];`, que as regex do teste Java leem. Depois o plugin apaga `.vite/`, que não deve ser público.
- **Nada de `/api` no cache.** Escrita, outra origem, `/api/` e pedidos com `Range` são ignorados pelo worker (`rotaDoPedido`).
- **Sem `skipWaiting`** (R3 da Etapa 1). O worker novo espera as abas fecharem. Enquanto isso, o antigo atende o bundle novo: a navegação vem da rede, e um arquivo `/app/` que ele não tem vem da rede e entra no cache dele. O JSON de `/assets/` vem da rede primeiro, para o JS novo não ler dados velhos.
- **`/app/` imutável.** O nome muda quando o conteúdo muda, então `/app/` vai do cache primeiro, como os binários de `/assets/`, e o servidor o entrega com `Cache-Control: immutable`. Os arquivos antigos só saem do cache no `activate` do worker novo, que apaga os caches `duora-*` de outra versão; o HTML guardado nunca cita um arquivo `/app/` que já saiu.

## Consequências

- O `sw.js` passa a ser gerado a cada build, e o de `static/` sai do Git no corte (Task 7); o cache do navegador muda só quando algum arquivo servido muda.
- O `ShellDoServiceWorkerTest` deixa de procurar `js/*.js` e passa a conferir os arquivos com hash: todo JS e CSS que o `index.html` gerado cita está no `SHELL` (Task 7).
- O build depende de um segundo build do Vite, pequeno, para o worker.
- Os testes que constroem o app sem manifest (CSS e avisos) não geram o `sw.js`: o plugin só age quando o manifest existe.

## Alternativas consideradas

- **Workbox (`vite-plugin-pwa`):** gera o precache do manifest e tem estratégias prontas, mas traz uma dependência grande, regras próprias de versão (um hash por arquivo, não a impressão que o teste Java confere) e um runtime que teria de ser revisto para não guardar `/api` nem respostas 206. O worker do Duora tem cerca de 100 linhas, e as regras dele já são testadas.
- **Versão manual (como na Etapa 1):** a lista e a versão ficariam velhas a cada build, porque os nomes com hash mudam; o teste Java falharia em todo commit que mexesse no front.
