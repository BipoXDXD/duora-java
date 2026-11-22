# Duora social — design e plano de etapas até o piloto aberto

## Contexto
A demonstração "Antes da primeira estrela" (Java, terraço para 2 pessoas, tudo em memória) está pronta e pausada. Em 2026-11-21 o dono redefiniu o produto: uma rede social para unir pessoas (amizade ou namoro) em que os cenários de criação compartilhada são o lugar do encontro. Requisitos dele: voz, pareamento influenciado por geolocalização e faixa etária, imagens melhores no celular, PWA instalável no Android.
Hipótese herdada (`duora-java-mvp.md` §1): uma pequena criação compartilhada aproxima as pessoas sem questionário, ranking ou pressão. O piloto existe para testá-la com gente real.

## Decisões do dono (brainstorming de 2026-11-21)
| Tema | Decisão |
|---|---|
| Ambição | **Piloto aberto**: cadastro público, sem convite, com pessoas reais. Começa pequeno e cresce conforme validar |
| Jornada | **Encontro primeiro**: a pessoa define filtros, é pareada e as duas fazem a atividade no cenário com apelido e foto borrada. Depois cada uma decide em privado; o perfil completo, o chat e a voz só abrem se as duas disserem sim |
| Quando | **Janelas marcadas** (ex.: ter/qui 21h–22h); a fila de pareamento só existe durante a janela |
| Stack | **Evoluir o Duora Java** (Spring Boot 4.1, Java 25, JS nativo) |
| Hospedagem | **VPS + Docker Compose** (Caddy, PostgreSQL, coturn no mesmo host), no padrão do `infra/` do projeto C# |
| Critério de namoro | **Gênero + quem quer conhecer**: compatível nos dois sentidos. Gênero em lista inclusiva mais "prefiro descrever" |
| LGPD | **Versão leve**: termos e política curtos, consentimento simples, exclusão de conta. Sem mapa de dados, relatório de impacto ou revisão jurídica obrigatória no piloto |
| Distância | **Raio opcional por pessoa**: cada um escolhe se o raio é obrigatório (filtro) ou só preferência (ordena, mais perto primeiro) |
| Duração | **Encontros de ~30 min; limite só de simultaneidade: até 10 encontros ao mesmo tempo** (20 pessoas). Não há teto por pessoa nem total por janela |
| Cenários no piloto | **Terraço + 4 novos: aquário, piquenique, cachoeira e jantar** (decisão do dono em 2026-11-22: "todos antes do piloto, ao menos 4"; escolha dos 4 delegada). Planetário, parque e fliperama ficam no backlog |
| Reencontro | **Só se ninguém disse "não"**. Um "não", uma saída ou uma denúncia bloqueiam o par para sempre; conectados não voltam a ser pareados; encontro interrompido sem decisão conta como "não" |

## Escolhas minhas (podem ser corrigidas)
- **18+ em todo o piloto**, inclusive para amizade. **Decisão do dono: autodeclaração + moderação.**
  - A data de nascimento é declarada no cadastro e não pode ser editada depois. Uma data abaixo de 18 bloqueia o cadastro e grava um hash do e-mail para impedir nova tentativa.
  - A denúncia "parece menor de idade" suspende a conta na hora, sem depender do admin, até a revisão.
  - Risco aceito pelo dono: a autodeclaração é frágil para namoro público.
- **Login**: link mágico por e-mail (Resend, já usado no C#), sem senha. O cadastro aberto ganha proteção contra robôs (Cloudflare Turnstile), rate limit por IP e por e-mail, e recusa de domínios de e-mail descartável.
- **Localização**: obtida pelo Geolocation do navegador, só com consentimento. O servidor guarda apenas a célula (geohash de precisão 5, ≈5 km) e a coordenada exata é descartada. A tela mostra faixas ("até 5 km", "até 20 km", "até 50 km") e nunca a distância exata.
- **Foto**: o servidor reprocessa a imagem (remove EXIF, converte para WebP q88) e gera a versão borrada. A original nítida só é servida a conexões mútuas, e o admin aprova cada foto antes de ela valer.
- **Voz só entre conexões mútuas** (WebRTC 1:1, apenas áudio, sem gravação). No encontro há só texto, o que reduz o assédio e facilita a moderação.
- **Tempo real**: SSE (servidor→cliente) mais POST, em vez de WebSocket. Assim reaproveito os filtros `MesmaOrigem`/`X-Duora` e o modelo MVC atual. O polling com ETag do terraço continua como plano B.
- **Sair, denunciar e bloquear** ficam visíveis em todas as telas desde a primeira etapa social. O bloqueio é bilateral e o bloqueado não fica sabendo.
- **Repo público + MIT**: mantidos, porque é só código, sem dados nem segredos. A revalidação acontece na Etapa 0.
- **Foto pendente não bloqueia**: quem ainda não teve a foto aprovada entra na fila com uma silhueta no lugar. Uma foto aprovada depois passa a valer nos encontros seguintes.
- **Excedente da capacidade**: com 10 encontros simultâneos já em andamento, quem chega espera na fila, vendo a posição e o tempo estimado.

## Não-objetivos do piloto
Verificação documental ou por selfie, matching por questionário ou score, salas em grupo, voz ou vídeo dentro do encontro, app nativo, monetização, cenários além dos 5 escolhidos, telemetria remota (lições do C#).

## Arquitetura alvo (monólito modular)
Pacotes em `bipotheking.net.duora`:
- `game/`: regras puras do terraço, inalteradas.
- `encontro/`: evolução de `room/`, com a sala vinculada a um par pareado.
- `conta/`: cadastro, login, sessão e exclusão.
- `perfil/`: apelido, nascimento, intenção, preferências, foto e célula de localização.
- `janela/`: agenda e presença.
- `pareamento/`: fila e algoritmo puro.
- `conexao/`: decisão privada, conexões e chat 1:1.
- `voz/`: sinalização WebRTC e credenciais TURN.
- `moderacao/`: denúncias, bloqueios, banimentos e auditoria.
- `notificacao/`: Web Push com VAPID.
- `admin/`: painel.

Persistência em PostgreSQL 18 com Flyway; os testes usam Testcontainers, rodando uma suíte com Docker por vez. O encontro em andamento é gravado como `jsonb` com versão (write-through do `Retrato`, lock otimista alinhado a `versaoEsperada`), para sobreviver a reinício. Há uma instância só.

## Etapas
Cada etapa termina com uma prova demonstrável, testes verdes (`./mvnw -q verify` + e2e afetados), handoff atualizado e commits limpos. A execução segue `superpowers:writing-plans`, com um plano detalhado por etapa, e depois `superpowers:subagent-driven-development` com subagentes Opus (máx. 4), TDD e revisão por etapa.

### Etapa 0: Reabertura e fundação
1. Salvar este design em `docs/superpowers/specs/2026-11-21-duora-social-design.md` e atualizar `_mapa.md` e o handoff.
2. Fazer o playtest humano e o aceite visual do terraço, que estão pendentes. O terraço é o coração do encontro; se não agradar, ajusta-se aqui, antes de construir em volta.
3. Corrigir o defeito real "foco após 409 fica num lugar bloqueado" (teste antes) e os minors baratos do ledger.
4. Revalidar repo público + MIT, criar o remoto no GitHub, fazer push e ver a CI rodar verde pela primeira vez.
5. Criar duas skills de projeto (`superpowers:writing-skills`):
   - `duora-assets`: pipeline Hi3D/Tripo → Blender → WebP q88/MP3 → `assets/manifest.md`.
   - `duora-etapa`: checklist de entrega (testes afetados, axe, handoff, resumo de decisões).

**Prova**: CI verde no GitHub; o dono aprova o terraço.

### Etapa 1: PWA e imagens no celular
- Manifest, ícones (192/512/maskable) e service worker com cache só do shell e dos assets versionados (nunca `/api`). Instalável no Android.
- Renders responsivos: o `cena.py` gera variantes 1x/2x/3x e retrato, servidas com `srcset`. O layout mobile ganha fallback sem `:has()`.
- Teste e2e com emulação de dispositivo móvel e checagem de instalabilidade (manifest + SW registrados).

**Prova**: instalado num Android real e terraço nítido no celular.

### Etapa 1b: Motor de cenários
- `game/` deixa de ter o terraço fixo no código: um `Cenario` descreve lugares, zonas, peças por participante, regras de colocação e como a composição vira o final. O terraço vira o primeiro cenário, descrito como dados, sem mudar nenhum comportamento: a suíte atual tem de continuar verde.
- O front lê a descrição do cenário (`cena.json` por cenário) em vez de constantes; os assets ficam em `assets/cena/<cenario>/`.
- A sala/encontro guarda qual cenário está em jogo. O pareamento (Etapa 4) sorteia um cenário que nenhum dos dois jogou ainda.

**Prova**: a suíte inteira segue verde com o terraço vindo de dados, e um cenário de teste mínimo (2 lugares, 2 peças) roda de ponta a ponta.

### Etapa 2: Persistência e contas
- PostgreSQL + Flyway + Testcontainers. Compose de desenvolvimento.
- Contas: e-mail + Turnstile → link mágico (token de uso único, hash no banco, expira em 15 min) → data de nascimento autodeclarada (bloqueia <18) → aceite de termos/política com versão registrada.
- Sessão em cookie `HttpOnly; Secure; SameSite=Lax` com o token guardado como hash, reaproveitando o padrão do `CookieAssento`. Rate limit no cadastro e no login.
- Exclusão de conta (apaga dados pessoais e anonimiza o que precisa ficar para moderação).

**Prova**: uma pessoa se cadastra pelo celular, um menor é recusado e um cadastro em rajada é barrado.

### Etapa 3: Perfil e preferências
- Perfil: apelido, intenção (amizade/namoro, podendo ser ambas), bio curta, foto (upload → EXIF removido → WebP → versão borrada → fila de aprovação).
- Preferências: faixa etária e raio (5/20/50 km/sem limite), com a opção "raio obrigatório" ou "só preferência". Para namoro: o próprio gênero e os gêneros que a pessoa quer conhecer, com um aviso curto e uma caixa de consentimento. Esses dados são apagados quando a pessoa desliga o modo namoro.
- Localização: consentimento explícito → célula geohash; atualizada a cada entrada na janela.

**Prova**: o perfil aparece borrado para terceiros e nítido só para o próprio titular.

### Etapa 4: Janelas, notificações e pareamento
- O admin cria janelas. O usuário confirma presença e recebe Web Push 15 min antes e quando a janela abre (pede permissão no PWA).
- Fila ativa só na janela. O algoritmo de pareamento é uma função pura e testável:
  - compatibilidade nos dois sentidos: intenção, idade, gênero × quem quer conhecer (namoro) e raio quando obrigatório para qualquer um dos dois;
  - exclui bloqueios, banidos, pares conectados e pares em que alguém disse "não";
  - ordena por espera mais longa e, entre os empatados, por menor distância;
  - respeita a capacidade de 10 encontros simultâneos.
- Roda em ciclos de N segundos. A pessoa volta à fila depois de cada encontro, quantas vezes quiser. Nenhum encontro novo começa se não couber 30 min antes do fim da janela.
- Estado vazio honesto: "ninguém compatível agora; você será avisado".

**Prova**: simulação com 30 perfis fictícios mostra pareamentos corretos, e o e2e com dois navegadores pareia e abre o terraço.

### Etapa 5: Encontro no cenário
- `room/` vira `encontro/`: a sala nasce do pareamento (sem convite) e o assento é ligado à conta. Dura ~30 min, com aviso aos 5 min finais. Ao expirar, os dois vão para a decisão privada.
- Na tela do encontro: apelido, foto borrada, a atividade do terraço e um chat de texto do encontro (SSE + POST, limite de tamanho e de frequência).
- Sair, denunciar e bloquear ficam sempre visíveis. Uma denúncia encerra o encontro para quem denunciou.
- Ao fim (revelação ou saída), cada pessoa responde em privado se quer continuar. O sim mútuo cria a conexão; o não nunca é revelado.

**Prova**: jornada completa em dois navegadores, do pareamento à decisão mútua; a recusa fica invisível para o outro.

### Etapa 6: Conexões e chat
- Lista de conexões e perfil completo com foto nítida. Chat 1:1 persistente (SSE, indicação de não lidas, push opcional).
- Desfazer a conexão e bloquear, com efeito imediato nos dois lados.

**Prova**: uma conversa sobrevive a reinício do servidor; o bloqueio corta o acesso na hora.

### Etapa 7: Voz
- WebRTC 1:1, só áudio, apenas entre conexões. A sinalização (offer/answer/ICE) passa pelo canal SSE + POST.
- coturn com credenciais temporárias (TURN REST, HMAC, TTL curto). Ligar, aceitar, recusar e encerrar; sem gravação.
- O e2e usa o fake media do Chromium. A prova real é uma ligação entre um Android em 4G e um desktop.

**Prova**: a chamada se completa através de NAT via TURN.

### Etapa 8: Moderação e painel admin
- Papel ADMIN.
- Filas de denúncias (com contexto limitado às mensagens denunciadas) e de fotos.
- Banimento, que derruba sessões e retira a pessoa das filas.
- Log de auditoria das ações de admin. Gestão de janelas. Com o cadastro aberto, a moderação precisa estar pronta antes da abertura, e o SLA de análise de denúncia precisa ser definido.

**Prova**: denúncia → análise → banimento ponta a ponta.

### Etapa 9: Termos e privacidade (versão leve)
- Termos de uso e política de privacidade curtos, em PT-BR, partindo dos modelos em `../../CSharp/Duora/docs/legal/`. Contato por e-mail.
- O básico que já vem das etapas anteriores: exclusão de conta, chat de encontro expirando em 30 dias e localização apenas como célula.

**Prova**: as duas páginas estão publicadas e linkadas no cadastro, e o job de expiração do chat é testado.

### Etapa 9b: Quatro cenários novos
- Um design curto por cenário (lugares, peças, o que cada composição muda na revelação), feito com brainstorming próprio antes dos assets.
- Assets pela skill `duora-assets`: props no Hi3D → Blender → WebP q88 em 900/1800/2700; fundo/céu no Tripo; som no ElevenLabs (MP3 192 kbps/48 kHz). Confirmar saldo antes de gerar em lote.
- Cada cenário entra com seus testes (regras + jornada e2e + axe) e passa por playtest do dono antes de valer no pareamento.
- Ordem: aquário, piquenique, cachoeira, jantar. Cada um é independente e pode rodar em paralelo.

**Prova**: os 5 cenários sorteáveis no pareamento, cada um aprovado no playtest do dono.

### Etapa 10: Infra e entrega
- VPS Ubuntu 24.04. Compose com `app`, `postgres`, `caddy` (TLS, cabeçalhos) e `coturn`. Firewall nas portas 443 e 3478/5349, mais a faixa de relay.
- Backup `pg_dump`/pgBackRest para armazenamento externo, com drill de restauração testado.
- Domínio, segredos fora do Git e deploy por GitHub Actions (imagem no GHCR → deploy via SSH) ou script.
- Health check, logs estruturados e monitor de disponibilidade.

**Prova**: o ambiente de homologação passa no smoke e2e contra a URL real, e a restauração do backup é comprovada.

### Etapa 11: Piloto aberto
- Abrir o cadastro e definir o calendário de janelas. A capacidade cresce aos poucos (limite de simultaneidade configurável).
- Formulário de feedback depois de cada encontro.
- Métricas internas, sem telemetria remota:
  - presença por janela;
  - encontros concluídos;
  - taxa de sim mútuo;
  - conversas iniciadas após a conexão;
  - denúncias.
- Critério de sucesso: participantes dizem "eu gostaria de fazer isso com alguém" e voltam na janela seguinte.
- Decisão ao final: ajustar, ampliar o piloto ou criar novos cenários.

## Dependências e paralelismo
0 → 1 → 1b → 2 → 3 → 4 → 5 → 6 → 7. A 9b começa depois da 1b e roda em paralelo às etapas 2–8 (assets e dados não dependem de contas). A 8 começa junto com a 5 (denúncia e bloqueio básicos já entram na 5). A 9 é curta e fecha antes da 10. A 10 pode ser preparada em paralelo a partir da 6. A 11 depende de todas.

## Verificação (por etapa e final)
- Unitários e API: `./mvnw -q verify`.
- E2E afetados: `./mvnw -q -Pe2e verify -Dit.test=...` no chromium, com a matriz firefox/webkit rodando na CI.
- `AxeIT` estendido a cada tela nova.
- Testcontainers para o banco, uma suíte com Docker por vez.
- Provas manuais listadas em cada etapa, num Android real.
- Final: smoke e2e contra homologação e uma janela de teste com o dono e mais uma pessoa antes do piloto.

## Backlog de cenários (ideias do dono, 2026-11-22)
Escolhidos para o piloto (Etapa 9b): aquário, piquenique, cachoeira e jantar. Ficam no backlog: planetário (repete as estrelas do terraço), parque (se sobrepõe ao piquenique) e fliperama (exige motor de jogo em tempo real). Cada cenário segue o formato do terraço: criação a dois, ~30 min, sem placar, revelação no fim. As atividades abaixo são propostas iniciais, a validar em brainstorming próprio.

| Cenário | Atividade compartilhada proposta |
|---|---|
| Parque | Montar juntos um canto do parque (banco, árvore, pipa, lago) e ver o pôr do sol mudar conforme a composição |
| Cachoeira | Escolher a trilha e o mirante; a combinação decide a luz e o arco-íris na revelação |
| Planetário | Cada um posiciona estrelas e os dois desenham uma constelação nova, batizada no fim |
| Aquário | Compor um tanque (plantas, pedras, peixes); a revelação acende a iluminação e os peixes nadam |
| Jantar | Montar a mesa e escolher pratos a dois; a revelação é o jantar servido com a trilha escolhida |
| Piquenique | Preparar a toalha e a cesta; o clima e os passantes reagem às escolhas |
| Fliperama | Jogo cooperativo original a dois (sem recriar jogos existentes); um recorde conjunto, não competição |
