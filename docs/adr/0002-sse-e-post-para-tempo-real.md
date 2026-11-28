# 0002 — SSE único por sessão e POST por domínio para o tempo real

- **Estado:** aceita
- **Data:** 2026-11-28
- **Etapa:** 4

## Contexto

Até a Etapa 3, o front só perguntava: a sala usa polling encadeado com `ETag`, que serve para duas pessoas e mudanças lentas. A Etapa 4 traz a fila de pareamento: a pessoa espera e precisa saber na hora que foi pareada, com 60 s para entrar. As Etapas 5 e 6 trazem chat, voz e conexões, que também empurram coisas do servidor para o cliente. Fazer polling de cada tela multiplicaria requisições e atrasos, e cada domínio inventaria a sua retomada depois de uma queda.

## Decisão

- **Um stream SSE por sessão, comandos por POST.** `GET /api/eventos` (`text/event-stream`) leva tudo o que o servidor empurra, de todos os domínios; o que o cliente pede continua nas rotas REST de cada domínio (`POST /api/fila`, `POST /api/encontros/...`), com as regras de sempre (sessão, `X-Duora`, mesma origem, idempotência). O stream não recebe comandos.
- **Duráveis com `seq` e trava por conta.** Um evento durável (`fila.estado`, `fila.pareado`) é gravado em `evento_conta` na transação de quem publica (`CanalDeEventos.publicar`, `MANDATORY`), depois de `pg_advisory_xact_lock` por conta em ordem crescente de UUID: o `seq` de cada conta cresce na ordem do commit, e um rollback não grava nem avisa. O `seq` é o `id:` do SSE. Os eventos ficam 1 dia (`RetencaoDeEventos`).
- **Retomada pelo banco.** Quem volta diz o último `id` recebido por `Last-Event-ID` (o `EventSource` manda sozinho) ou `?ultimoId=` (o cabeçalho vence). Se esse `id` ainda está guardado (entre o menor e o maior `seq` da conta), o servidor reenvia o que veio depois, até 500; senão, ou se sobrou, manda o estado inicial de cada módulo (`EstadoInicial`) e segue do mais novo. Nenhuma perda depende de o cliente estar conectado.
- **Efêmeros sem `id`.** O que só vale para quem está olhando agora (`CanalDeEventos.publicarEfemero`, o estado inicial) vai direto aos streams abertos, sem gravar e sem `id`: não entra na retomada.
- **Transporte em `stream.*`.** `stream.batimento` (`{}`, a cada 25 s sem outro evento) mantém a conexão viva nos proxies e revela o cliente que sumiu; `stream.substituido` (`{}`) avisa o stream mais antigo antes de fechá-lo quando a conta abre o quarto. Só o `StreamsSse` escreve `stream.*`; o `Evento` recusa o prefixo. O primeiro evento traz `retry:` sorteado entre 2000 e 5000 ms, para as reconexões depois de um deploy não chegarem juntas.
- **Entrega por aviso e leitura.** O commit não empurra o evento: só acorda os streams da conta (`EntregaDeEventos.acordar`), e cada stream lê do banco o que veio depois do último que enviou. Cada stream tem um escritor numa virtual thread e uma fila de 100 itens; avisos repetidos se juntam numa leitura só. A fila cheia fecha o stream (`duora.eventos.estouros`), e o cliente volta pelo `id` sem perder nada. O stream dura no máximo 30 min (`duora.eventos.duracao-maxima`) e reconecta.
- **Cabeçalhos:** `Cache-Control: no-store` e `X-Accel-Buffering: no`. Métricas: gauge `duora.eventos.streams` e contadores `duora.eventos.aberturas` e `duora.eventos.estouros`.

## Alternativas consideradas

- **WebSocket:** bidirecional, mas o Duora não precisa mandar comandos pelo canal; teria de reimplementar sessão, CSRF, limite de taxa e retomada que o HTTP já dá, e proxies e redes móveis o tratam pior. A voz da Etapa 6 usa WebRTC, não este canal. Rejeitada.
- **Polling por domínio (como a sala):** simples e sem conexão aberta, mas o pareado esperaria até o próximo pedido, e cada tela somaria requisições. Rejeitada para o que é empurrado; a sala segue com o seu polling.
- **Outbox genérica com despachante:** reusaria a `notificacao/Outbox`, mas acrescentaria o atraso do despachante (2 s) e um segundo lugar com estado de entrega por cliente. A tabela própria com `seq` por conta já é a fila, e o stream lê dela. Rejeitada.

## Consequências

- **Uma instância só.** O registro de streams é em memória: `conectada` e os avisos valem nesta instância. Com duas, o aviso de uma não acordaria o stream aberto na outra (o evento ficaria gravado e chegaria na reconexão). Escalar pede `LISTEN/NOTIFY` do PostgreSQL ou equivalente (Etapa 10).
- **Traefik.** O Traefik do Coolify não bufferiza respostas por padrão, mas o `readTimeout` do entrypoint (60 s no v3) pode cortar a conexão. O cliente volta com o último `id` sem perda; `duora.eventos.aberturas` por minuto muito acima do número de pessoas online denuncia o corte, e a correção é `readTimeout=0` no entrypoint (README, "Deploy (Coolify)").
- **Uma thread de Tomcat por stream não fica presa:** a resposta é assíncrona, e o escritor é virtual. Cada leitura do banco usa uma conexão do pool só pelo tempo da consulta.
- No desligamento, os streams fecham antes da espera graciosa do servidor, e os clientes reconectam na instância nova.
