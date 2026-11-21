---
name: duora-etapa
description: Use ao concluir, encerrar ou entregar uma task, etapa ou passo de plano do Duora, antes de dizer "pronto", fazer o commit ou parar para o dono revisar, e quando o dono disser "pausa", "handoff" ou "vou parar".
---

# Fechamento de task ou etapa do Duora

## Visão geral

Uma task só está concluída quando os oito itens abaixo estão feitos, **nesta ordem**, com a saída de cada comando vista por você. Afirmar sem rodar não vale. Pular um item porque "foi só CSS", "é só doc" ou "o CI pega" não vale.

## Checklist

Crie um todo por item e marque só depois da evidência.

### 1. Testes afetados

Antes de qualquer `./mvnw`, garanta que nenhum outro Maven pesado está rodando (um por vez na máquina):

```sh
pgrep -fl "[c]lassworlds\.launcher" | grep -v RemoteMavenServer   # tem de sair vazio
```

| Mudou | Comando |
|---|---|
| Qualquer coisa em `src/` ou `pom.xml` | `./mvnw -q verify` |
| `src/main/resources/static/`, `e2e/` ou fluxo da página | também `./mvnw -q -Pe2e verify -Dnavegador=chromium`, depois `firefox`, depois `webkit` (um de cada vez) |
| Só `*.md`, `assets/source/` sem export novo ou `.claude/` | nenhum Maven; vá ao item 2 |

Para iterar, filtre (`-Dtest=RegrasTest`, `-Dit.test=AxeIT`), mas o portão final é o comando da tabela, sem filtro. Proibido `-DskipTests`, `-DskipITs`, `-Dspotless.check.skip`, `@Disabled` ou `assumeTrue` para passar. Falhou: corrija a causa e rode de novo.

### 2. `AxeIT` cobre a tela nova

Tela, estado ou bloco visível novo exige um caso novo em `src/test/java/bipotheking/net/duora/e2e/AxeIT.java`: leve a página até o estado e chame `assertSemViolacoes(pagina)`. Um comportamento por teste (`<estado>SemViolacoes`). Não amplie `DECORATIVOS` para calar violação: corrija o HTML/CSS.

### 3. Spotless

Mudou algum `.java`:

```sh
./mvnw -q spotless:apply
git status --short
```

O `verify` já roda o `spotless:check`. Se o `apply` mudou algum arquivo, repita o item 1.

### 4. Greps de caminho local e segredo

Sobre o que vai entrar no commit (`git add` dos seus caminhos antes):

```sh
git diff --cached -U0 | grep -nE "/Use[r]s/|$(whoami)|@gmai[l]\.com"
git diff --cached -U0 | grep -nEi "(api[_-]?key|secret|token|senha|password)[\"' ]*[:=]|sk-[A-Za-z0-9]{16,}|BEGIN [A-Z ]*PRIVATE KEY"
git diff --cached --name-only | grep -nE "\.env|settings\.local\.json|\.claude/handoff/|\.glb$"
```

Os três têm de sair vazios. Achou algo: troque por caminho relativo ao repositório ou tire do commit.

### 5. Commit

- Só os seus arquivos: `git add <caminhos>` e `git commit -- <caminhos>`. Nunca `git add -A` ou `git add .` (outras sessões trabalham no mesmo repositório).
- Conventional Commits em pt-BR, voz ativa: `feat(ui): …`, `fix(room): …`, `test(e2e): …`, `docs: …`, `chore: …`.
- Última linha da mensagem: `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Sem push, a não ser que o dono peça.

### 6. Handoff

Arquivo `.claude/handoff/<branch-ou-tarefa>.md` no **repositório principal** (em worktree, a raiz é a pasta pai de `git rev-parse --path-format=absolute --git-common-dir`). É fora do Git e é só da sua sessão: não edite o handoff de outra. Substitua o conteúdo velho em vez de acumular. Seções, nesta ordem:

```markdown
## Objetivo
## Decisões tomadas
(o quê e por quê)
## Arquivos alterados
## Estado atual
(testes rodados e resultado; SHA do commit)
## Próximos passos
## Pendências/riscos
```

Mudou a estrutura do projeto (pasta, módulo, comando)? Faça uma edição pequena e pontual em `.claude/handoff/_mapa.md`.

### 7. Resumo de decisões para o dono

Em pt-BR, poucas linhas, em tópicos: cada decisão tomada, com o quê e por quê. Sem aula, sem repetir código. Se houve decisão difícil de desfazer (arquitetura, biblioteca, modelo de dados, custo, segurança), ela não devia ter sido tomada sozinha: apresente as opções e espere o dono.

### 8. Linha final

A resposta termina exatamente com:

```
Etapa concluída · handoff atualizado (<caminho>) · Recomendo /clear e retomar com "leia <caminho> e continue".
```

`<caminho>` é o caminho do handoff do item 6, relativo à raiz do repositório principal.

## Sinais de que você está pulando etapa

| Pensamento | Realidade |
|---|---|
| "Só mexi em CSS, não precisa de E2E" | CSS muda contraste, foco e layout: `-Pe2e` nos três navegadores |
| "O CI roda o resto" | O CI é a rede, não o teste; rode localmente |
| "A tela nova é pequena, o axe existente cobre" | O axe só vê os estados que algum teste abre |
| "`git add -A` é mais rápido" | Pega arquivo de outra sessão ou handoff |
| "Atualizo o handoff depois" | A sessão pode acabar no `/clear`; atualize agora |
| "O resumo é óbvio pelo diff" | O dono revisa pelo resumo, não pelo diff |
