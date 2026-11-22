# Duora social — design e plano de etapas até o piloto aberto

## Contexto
A demonstração "Antes da primeira estrela" (Java, terraço para 2 pessoas, tudo em memória) está pronta e pausada. Em 2026-11-21 o dono redefiniu o produto: uma rede social para unir pessoas (amizade ou namoro) em que os cenários de criação compartilhada são o lugar do encontro. Requisitos dele: voz, pareamento influenciado por geolocalização e faixa etária, imagens melhores no celular, PWA instalável no Android.
Hipótese herdada (`duora-java-mvp.md` §1): uma pequena criação compartilhada aproxima as pessoas sem questionário, ranking ou pressão. O piloto existe para testá-la com gente real.

## Decisões do dono (brainstorming de 2026-11-21)
| Tema | Decisão |
|---|---|
| Ambição | **Piloto aberto**: cadastro público, sem convite, com pessoas reais. Começa pequeno e cresce conforme validar |
| Jornada | **Encontro primeiro**: a pessoa define filtros, é pareada e as duas fazem a atividade no cenário com apelido e foto borrada. Depois cada uma decide em privado; o perfil completo, o chat e a voz só abrem se as duas disserem sim |
| Quando | **Sem horário fixo** (dono, 2026-11-22). Quem abre o encontro escolhe: **público** (o pareamento traz alguém compatível a qualquer hora) ou **privado** (só por convite, via link). Substitui as janelas marcadas |
| Stack | **Evoluir o Duora Java** (Spring Boot 4.2, Java 25); front em TypeScript + React + Tailwind (Vite) a partir da Etapa 1a |
| Hospedagem | **VPS + Docker Compose** (Caddy, PostgreSQL, coturn no mesmo host), no padrão do `infra/` do projeto C# |
| Critério de namoro | **Gênero + quem quer conhecer**: compatível nos dois sentidos. Gênero em lista inclusiva mais "prefiro descrever" |
| LGPD | **Versão leve**: termos e política curtos, consentimento simples, exclusão de conta. Sem mapa de dados, relatório de impacto ou revisão jurídica obrigatória no piloto |
| Distância | **Raio opcional por pessoa**: cada um escolhe se o raio é obrigatório (filtro) ou só preferência (ordena, mais perto primeiro) |
| Duração | **30 min por padrão**; os dois podem encerrar antes **se ambos concordarem**. Limite só de simultaneidade: até 10 encontros ao mesmo tempo (20 pessoas) |
| Comunicação no encontro | (dono, 2026-11-22) **voz habilitada**, **mensagens de texto com links**, e **imagens** (só imagens, com aviso explícito e só se os dois concordarem) |
| Cenários no piloto | **Terraço + 5 novos: aquário, piquenique, cachoeira, jantar e fliperama** (dono, 2026-11-22: "todos antes do piloto, ao menos 4", escolha delegada; depois pediu o fliperama). Planetário e parque ficam no backlog |
| Reencontro | **Só se ninguém disse "não"**. Um "não", uma saída ou uma denúncia bloqueiam o par para sempre; conectados não voltam a ser pareados; encontro interrompido sem decisão conta como "não" |

## Escolhas minhas (podem ser corrigidas)
- **18+ em todo o piloto, com verificação de idade** (dono, 2026-11-22; substitui a autodeclaração). Motivo: o ECA Digital (Lei 15.211/2025, art. 9º §1º, em vigor desde 17/03/2026) veda a autodeclaração, e o Decreto 12.880/2026 inclui apps de encontros entre os serviços vedados a menores. A fiscalização começa em jan/2027 (fonte: `docs/pesquisa/seguranca-ux-mobile.md`; não é parecer jurídico).
  - **Método:** prova de vida mais estimativa de idade pela selfie, feitas na **AWS** (Rekognition Face Liveness e DetectFaces AgeRange). O documento é a reserva, só para quem cai na faixa de dúvida (`AgeRange.Low < 21` e `AgeRange.High ≥ 18`), feita pela **Didit** (lê RG e CNH brasileiros e compara o rosto do documento com a selfie da prova de vida). Selfie e documento são apagados logo após a checagem (na Didit, chamadas com `save_api_request=false`), e fica só o resultado (aprovado ou recusado, data, método).
  - **Reserva por documento (dono, 2026-11-22):** Didit no lugar do Textract `AnalyzeID`, porque a AWS documenta o `AnalyzeID` só para documentos dos EUA. Ligada por padrão. A chave da Didit fica no Keychain e, na VPS, no `.env`.
  - **Infra AWS (2026-11-22):** a conta de workload do Duora já tem a política da organização (SCP) que libera só `us-east-1`. A prova de vida funciona lá; registrar na política de privacidade a transferência internacional. Para o Java existem o usuário IAM `duora-java-verificacao` (CreateFaceLivenessSession, GetFaceLivenessSessionResults, DetectFaces, AnalyzeID e AssumeRole) e o papel `duora-java-liveness-captura` (só StartFaceLivenessSession), que gera a credencial curta do navegador, no mesmo padrão do C#. Ainda não há chaves de acesso: elas são criadas na Etapa 2 e guardadas no Keychain, sem entrar no Git. Os orçamentos `duora-rekognition-5usd` e `duora-mensal-5usd` já existem.
  - **SCP liberada (2026-11-22):** `textract:AnalyzeID` entrou na lista de ações permitidas em `us-east-1` da política `AdvancedModeRegionRestrictionSecurityControlPolicy`. A conta de workload alcança o AnalyzeID, mas o Duora não o usa (a reserva é a Didit); a permissão sai da política do usuário `duora-java-verificacao` na Etapa 2.
  - **Front:** usar o `FaceLivenessDetector` oficial do Amplify UI, carregado sob demanda e só na rota da selfie, fora do orçamento de 90 KB. A rota tem uma CSP própria, que libera câmera, WASM, `cdn.liveness.rekognition.amazonaws.com` e `wss://streaming-rekognition.us-east-1.amazonaws.com`, como no C#.
  - **Selo:** quem passa recebe o selo "verificado". O filtro "só encontros com verificados" saiu (dono, 2026-11-22): a verificação é obrigatória, então ele não teria efeito.
  - A data de nascimento continua declarada e imutável e precisa bater com a verificação. Abaixo de 18, o cadastro é bloqueado e um hash do e-mail é gravado para impedir nova tentativa.
  - A denúncia "parece menor de idade" suspende a conta na hora, sem depender do admin, até a revisão.
- **Login**: link mágico por e-mail (Resend, já usado no C#), sem senha. O cadastro aberto ganha proteção contra robôs (Cloudflare Turnstile), rate limit por IP e por e-mail, e recusa de domínios de e-mail descartável.
- **Localização**: obtida pelo Geolocation do navegador, só com consentimento. O servidor guarda apenas a célula (geohash de precisão 5, ≈5 km) e a coordenada exata é descartada. A tela mostra faixas ("até 5 km", "até 20 km", "até 50 km") e nunca a distância exata.
- **Foto**: o servidor reprocessa a imagem (remove EXIF, converte para WebP q88) e gera a versão borrada. A original nítida só é servida a conexões mútuas, e o admin aprova cada foto antes de ela valer.
- **Voz** (WebRTC 1:1, só áudio, sem gravação no servidor) já no encontro, por decisão do dono. Cada um liga o próprio microfone; ninguém é ouvido sem ter ligado. Botão de silenciar o outro sempre visível.
  - **Prova de denúncia de voz** (dono, 2026-11-22): o aparelho de cada pessoa guarda em memória só os últimos 60 s do áudio recebido. Nada sai do aparelho, a menos que ela denuncie e confirme o envio do clipe. O aviso disso aparece no início de todo encontro, e a memória é descartada ao fim.
- **Sair é sempre unilateral** (segurança): qualquer um sai a qualquer momento e vai à decisão privada. O que exige os dois é **encerrar antes dos 30 min de comum acordo**.
- **Links:** domínio em destaque, só `http(s)` (nada de `javascript:`/`data:`), `rel="noopener noreferrer nofollow ugc"`, aviso "você vai sair do Duora" ao clicar, sem prévia automática.
- **Imagens:** liberadas só quando os dois aceitam após um aviso explícito (e qualquer um desliga a qualquer momento); até 5 MB; reprocessadas no servidor (WebP q88, sem metadados); chegam borradas com toque para ver; denunciáveis; expiram com o chat do encontro (30 dias).
- **Sem imagens sensíveis** (dono, 2026-11-22): nudez, sexo, violência explícita e conteúdo chocante são proibidos nos termos e no aviso de envio. Denúncia de imagem a esconde na hora para quem denunciou e a manda à fila do admin; confirmada, a conta é banida (sessões derrubadas, fora da fila e das conexões).
- **Tempo real**: SSE (servidor→cliente) mais POST, em vez de WebSocket. Assim reaproveito os filtros `MesmaOrigem`/`X-Duora` e o modelo MVC atual. O polling com ETag do terraço continua como plano B.
- **Sair, denunciar e bloquear** ficam visíveis em todas as telas desde a primeira etapa social. O bloqueio é bilateral e o bloqueado não fica sabendo.
- **Repo público + MIT**: mantidos, porque é só código, sem dados nem segredos. A revalidação acontece na Etapa 0.
- **Foto pendente não bloqueia**: quem ainda não teve a foto aprovada entra na fila com uma silhueta no lugar. Uma foto aprovada depois passa a valer nos encontros seguintes.
- **Excedente da capacidade**: com 10 encontros simultâneos já em andamento, quem chega espera na fila, vendo a posição e o tempo estimado.

## Não-objetivos do piloto
Matching por questionário ou score, salas em grupo, voz ou vídeo dentro do encontro, app nativo, monetização, cenários além dos 5 escolhidos, telemetria remota (lições do C#).

## Paridade com o mercado (pesquisa de 2026-11-22)
As fontes ficam em `docs/pesquisa/` (namoro, amizade e atividades, segurança e UX mobile). Itens adotados, por etapa:
- **Etapa 2:**
  - verificação de idade e selo (acima);
  - limites para contas novas (mensagens, imagens e convites por dia nos primeiros 7 dias);
  - logs de acesso guardados por 6 meses (Marco Civil);
  - canal público de denúncia (ECA Digital, arts. 28 e 29);
  - contato do encarregado da LGPD.
- **Etapa 3:**
  - gênero, orientação e pronomes granulares, com "prefiro descrever";
  - pausar a conta sem apagá-la.
- **Etapa 4:**
  - liquidez visível: tempo de espera estimado e horários com mais gente;
  - notificações confiáveis e discretas, com texto que não expõe nada na tela bloqueada.
- **Etapa 5:**
  - sair, silenciar, denunciar e bloquear a um toque, dentro da cena;
  - avisos "Tem certeza?" antes de enviar algo ofensivo e "Isso te incomodou?" depois de receber;
  - alerta de troca de contato ou de link e de golpe via PIX ("golpe do amor");
  - triagem automática das imagens antes da entrega: modelo NSFW open source (Falconsai, Apache-2.0) via ONNX Runtime Java, mais o fluxo de abuso infantil por hash (Cloudflare CSAM Scanning Tool), com remoção, preservação e reporte à Polícia Federal (ECA Digital, art. 27);
  - clipe de voz para denúncia (acima);
  - perguntas embutidas nos objetos da cena como quebra-gelo;
  - cartão-postal do encontro que os dois guardam;
  - feedback pós-encontro.
- **Etapa 6:**
  - próximo passo depois do sim, com lembrete gentil contra o sumiço (anti-ghosting);
  - ocultar conversa;
  - notificação por conversa.
- **Etapa 8:**
  - fila com severidade (P0 ≤ 12 h, P1 ≤ 24 h, P2 ≤ 48 h);
  - aviso ao denunciado com o motivo e um recurso por decisão (tese do STF de 2025);
  - Sightengine como reserva paga opcional, desligada por padrão.
- **Etapa 9:**
  - central de segurança com recursos do Brasil (Ligue 180, SaferNet, polícia);
  - termos com remoção após notificação;
  - RIPD curto;
  - plano de incidente com comunicação em 3 dias úteis.
- **Etapas 1c e 9b:**
  - abertura sem palavras no terraço (acender as lanternas juntos);
  - papéis que se alternam no "Dois faróis";
  - criação em turnos com revelação no fim.
- **Backlog (experimento no piloto):** desfoque que diminui com marcos de cooperação, só com o consentimento dos dois (dono, 2026-11-22: "não agora; testar no piloto").
- **Não copiar:**
  - paywall em segurança e em "ver quem curtiu";
  - vídeo como centro do produto (Tinder e Hinge removeram);
  - dark patterns de retenção (streaks punitivos, escassez falsa).

## Arquitetura alvo (monólito modular)
Pacotes em `bipotheking.net.duora`:
- `game/`: regras puras do terraço, inalteradas.
- `encontro/`: evolução de `room/`, com a sala vinculada a um par pareado.
- `conta/`: cadastro, login, sessão e exclusão.
- `perfil/`: apelido, nascimento, intenção, preferências, foto e célula de localização.
- `sala/`: encontros públicos e privados (convite), fila contínua.
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

**Prova**: instalado no emulador Android e terraço nítido nas capturas, verificado por agente.

### Etapa 1a: Front em TypeScript, React e Tailwind
Decisão do dono (2026-11-22): o front migra antes da 1b, porque as etapas seguintes são quase todas interface.
- Vite com React e TypeScript `strict`, mais Tailwind. CSS próprio só onde o Tailwind não ajuda (a cena: sprites, céu, meteoros, brilho), e a cena continua imperativa dentro de um componente com ref.
- O build roda pelo Maven (Node fixado, sem instalação global), e a saída vai para `static/`. A CI usa o mesmo caminho.
- O service worker passa a ler o manifest do Vite (arquivos com hash) em vez de uma lista fixa de arquivos. A guarda de versão por impressão continua valendo.
- A CSP não muda (sem `unsafe-inline` nem `unsafe-eval`). Nenhuma dependência de CDN.
- Comportamento, textos e visual idênticos. Os seletores dos testes por papel e texto continuam valendo.

**Prova**: os e2e e o axe verdes nos 3 navegadores sem mudar asserções, o visual igual por comparação de capturas antes e depois, e o tamanho do JS inicial medido e registrado (meta: ≤ 80 KB gzip).

### Etapa 1b: Motor de cenários
- `game/` deixa de ter o terraço fixo no código: um `Cenario` descreve lugares, zonas, peças por participante, regras de colocação e como a composição vira o final. O terraço vira o primeiro cenário, descrito como dados, sem mudar nenhum comportamento: a suíte atual tem de continuar verde.
- O front lê a descrição do cenário (`cena.json` por cenário) em vez de constantes; os assets ficam em `assets/cena/<cenario>/`.
- A sala/encontro guarda qual cenário está em jogo. O pareamento (Etapa 4) sorteia um cenário que nenhum dos dois jogou ainda.

**Prova**: a suíte inteira segue verde com o terraço vindo de dados, e um cenário de teste mínimo (2 lugares, 2 peças) roda de ponta a ponta.

### Etapa 1c: Ajustes do playtest (motor de encontro)
Playtest por agente (2026-11-22): primeira ação clara; contribuição do outro pouco visível; negociação rasa (4 decisões, < 1 min); final quase não muda; pouca vontade de repetir. Decisões do dono:
- **Ver o que o outro fez:** a peça que mudou ganha destaque animado na cena e a linha de status sobe para logo abaixo dos nomes (visível em 835–900 px de altura e no celular rolado).
- **Sinal com destino:** ao sugerir, a pessoa aponta um lugar livre ("Que tal aqui?"); o outro aceita (move) ou ignora. Sem texto livre.
- **Efeito por peça na revelação:** cada peça tem efeito próprio (rádio toca notas mesmo com o som geral desligado, manta brilha, garrafa solta vapor, etc.) e o texto do resultado considera posições relativas. Definido por cenário, como dados do motor (1b).
- **Concordância para encerrar:** "Ajustar" depois da revelação pede o aceite do outro; "Sair" leva quem saiu à decisão privada e o outro vê uma mensagem gentil ("o encontro terminou"). A revelação ganha modo tela cheia no celular.
- Correções sem decisão (achadas no playtest): "por o" → "pelo"; "Você foi chamado" neutro; tirar a dica de janela anônima e "Esc cancela" no celular; "O que falta" some quando nada falta; contorno de foco do título do resultado; convite novo na mesma aba troca de sala; 401 da primeira carga sem erro no console.
- Vem depois da 1b, para que destaque, sinal com destino e concordância sejam do motor (valem para todos os cenários).

**Prova**: novo playtest por agente responde "sim" às perguntas 2–5, com evidência em capturas.

### Etapa 2: Persistência e contas
- PostgreSQL + Flyway + Testcontainers. Compose de desenvolvimento.
- Contas: e-mail + Turnstile → link mágico (token de uso único, hash no banco, expira em 15 min) → data de nascimento autodeclarada (bloqueia <18) → aceite de termos/política com versão registrada.
- Sessão em cookie `HttpOnly; Secure; SameSite=Lax` com o token guardado como hash, reaproveitando o padrão do `CookieAssento`. Rate limit no cadastro e no login.
- Exclusão de conta (apaga dados pessoais e anonimiza o que precisa ficar para moderação).

**Prova**: uma pessoa se cadastra pelo celular, um menor é recusado e um cadastro em rajada é barrado.

### Etapa 3: Perfil e preferências
- Perfil: apelido, intenção (amizade/namoro, podendo ser ambas), bio curta, foto (upload → EXIF removido → WebP → versão borrada → fila de aprovação).
- Preferências: faixa etária e raio (5/20/50 km/sem limite), com a opção "raio obrigatório" ou "só preferência". Para namoro: o próprio gênero e os gêneros que a pessoa quer conhecer, com um aviso curto e uma caixa de consentimento. Esses dados são apagados quando a pessoa desliga o modo namoro.
- Localização: consentimento explícito → célula geohash; atualizada a cada entrada na fila.

**Prova**: o perfil aparece borrado para terceiros e nítido só para o próprio titular.

### Etapa 4: Encontros públicos e privados e pareamento
- Sem horário fixo. Quem abre um encontro escolhe **público** ou **privado**:
  - privado: gera um convite de uso único (link), como o terraço de hoje; só quem tem o link entra;
  - público: entra na fila contínua; o pareamento traz alguém compatível.
- O algoritmo de pareamento é uma função pura e testável:
  - compatibilidade nos dois sentidos: intenção, idade, gênero × quem quer conhecer (namoro) e raio quando obrigatório para qualquer um dos dois;
  - exclui bloqueios, banidos, pares conectados e pares em que alguém disse "não";
  - ordena por espera mais longa e, entre os empatados, por menor distância;
  - respeita a capacidade de 10 encontros simultâneos (excedente espera com posição visível).
- Roda em ciclos de N segundos; quem espera vê o tempo e pode desistir. Web Push opcional: "alguém compatível está esperando agora" (pede permissão no PWA).
- Estado vazio honesto: "ninguém compatível agora; você será avisado".
- Sorteio do cenário: um que nenhum dos dois jogou; em encontro privado, quem abre pode escolher o cenário.

**Prova**: simulação com 30 perfis fictícios mostra pareamentos corretos; e2e com dois navegadores cobre público (pareia e abre o cenário) e privado (convite).

### Etapa 5: Encontro no cenário
- `room/` vira `encontro/`: a sala nasce do pareamento (sem convite) e o assento é ligado à conta. Dura 30 min por padrão, com aviso aos 5 min finais. Os dois podem encerrar antes se ambos concordarem (pedido + aceite). Sair é sempre possível sozinho. Ao fim, os dois vão para a decisão privada.
- Na tela do encontro: apelido, foto borrada, a atividade do cenário e o chat do encontro (SSE + POST, limite de tamanho e de frequência):
  - texto com links (regras de links acima);
  - voz (Etapa 7, que passa a ser pré-requisito desta);
  - imagens com consentimento dos dois (regras de imagens acima), com aviso explícito antes de habilitar.
- Sair, denunciar e bloquear ficam sempre visíveis. Uma denúncia encerra o encontro para quem denunciou.
- Ao fim (revelação ou saída), cada pessoa responde em privado se quer continuar. O sim mútuo cria a conexão; o não nunca é revelado.

**Prova**: jornada completa em dois navegadores, do pareamento à decisão mútua; a recusa fica invisível para o outro.

### Etapa 6: Conexões e chat
- Lista de conexões e perfil completo com foto nítida. Chat 1:1 persistente (SSE, indicação de não lidas, push opcional).
- Desfazer a conexão e bloquear, com efeito imediato nos dois lados.

**Prova**: uma conversa sobrevive a reinício do servidor; o bloqueio corta o acesso na hora.

### Etapa 7: Voz
- WebRTC 1:1, só áudio, no encontro e entre conexões. Microfone desligado ao entrar; cada um liga o seu. A sinalização (offer/answer/ICE) passa pelo canal SSE + POST.
- coturn com credenciais temporárias (TURN REST, HMAC, TTL curto). Ligar, aceitar, recusar e encerrar; sem gravação.
- O e2e usa o fake media do Chromium. A prova real é uma ligação entre um Android em 4G e um desktop.

**Prova**: a chamada se completa através de NAT via TURN.

### Etapa 8: Moderação e painel admin
- Denúncia de imagem e de link do chat, com a imagem/mensagem como contexto. Imagem sensível confirmada → banimento.
- Papel ADMIN.
- Filas de denúncias (com contexto limitado às mensagens denunciadas) e de fotos.
- Banimento, que derruba sessões e retira a pessoa das filas.
- Log de auditoria das ações de admin. Com o cadastro aberto, a moderação precisa estar pronta antes da abertura, e o SLA de análise de denúncia precisa ser definido.

**Prova**: denúncia → análise → banimento ponta a ponta.

### Etapa 9: Termos e privacidade (versão leve)
- Termos de uso e política de privacidade curtos, em PT-BR, partindo dos modelos em `../../CSharp/Duora/docs/legal/`. Contato por e-mail.
- O básico que já vem das etapas anteriores: exclusão de conta, chat de encontro expirando em 30 dias e localização apenas como célula.

**Prova**: as duas páginas estão publicadas e linkadas no cadastro, e o job de expiração do chat é testado.

### Etapa 9b: Cenários novos
- Um design curto por cenário (lugares, peças, o que cada composição muda na revelação), feito com brainstorming próprio antes dos assets.
- Assets pela skill `duora-assets`: props no Hi3D → Blender → WebP q88 em 900/1800/2700; fundo/céu no Tripo; som no ElevenLabs (MP3 192 kbps/48 kHz). Confirmar saldo antes de gerar em lote.
- Cada cenário entra com seus testes (regras + jornada e2e + axe) e passa por playtest do dono antes de valer no pareamento.
- Ordem: aquário, piquenique, cachoeira, jantar (motor de composição, independentes, podem rodar em paralelo); fliperama por último (ver abaixo).
- **Fliperama** (escolhas minhas, o dono pode reverter):
  - jogo cooperativo **original** a dois (não recria jogo existente), rodadas de ~3 min, recorde conjunto, sem competição entre os dois;
  - feito em **Godot 4** (conduzido pelo MCP do Godot), export web **single-thread** (dispensa COOP/COEP), servido em `/jogos/fliperama/` e embutido no encontro por iframe da mesma origem;
  - sincronia em tempo real por **WebSocket no Spring** (servidor autoritativo: simulação pura em Java a 20 Hz, entradas com `seq` por assento e alvo absoluto, ordem pelo `tick`; o cliente Godot só desenha; o servidor guarda o placar conjunto); depende do canal de tempo real da Etapa 5;
  - CSP: a página do jogo precisa de `script-src 'self' 'wasm-unsafe-eval'` e de `frame-ancestors 'self'` só nela; o resto do app mantém a CSP atual;
  - assets: sprites/fundos no Tripo (2D), música e efeitos no ElevenLabs (MP3 192 kbps/48 kHz), Higgsfield se precisar de animação; o export web vai para o cache do SW;
  - prova extra: o jogo abre em até 5 s num Android médio em 4G com cache frio.

**Prova**: os 6 cenários sorteáveis no pareamento, cada um aprovado no playtest do dono.

### Etapa 10: Infra e entrega
- VPS Ubuntu 24.04. Compose com `app`, `postgres`, `caddy` (TLS, cabeçalhos) e `coturn`. Firewall nas portas 443 e 3478/5349, mais a faixa de relay.
- Backup `pg_dump`/pgBackRest para armazenamento externo, com drill de restauração testado.
- Domínio, segredos fora do Git e deploy por GitHub Actions (imagem no GHCR → deploy via SSH) ou script.
- Health check, logs estruturados e monitor de disponibilidade.

**Prova**: o ambiente de homologação passa no smoke e2e contra a URL real, e a restauração do backup é comprovada.

### Etapa 11: Piloto aberto
- Abrir o cadastro. A capacidade cresce aos poucos (limite de simultaneidade configurável).
- Formulário de feedback depois de cada encontro.
- Métricas internas, sem telemetria remota:
  - encontros públicos e privados por dia;
  - encontros concluídos;
  - taxa de sim mútuo;
  - conversas iniciadas após a conexão;
  - denúncias.
- Critério de sucesso: participantes dizem "eu gostaria de fazer isso com alguém" e voltam para outro encontro.
- Decisão ao final: ajustar, ampliar o piloto ou criar novos cenários.

## Dependências e paralelismo
0 → 1 → 1a → 1b → 1c → 2 → 3 → 4 → 7 → 5 → 6 (a voz vem antes do encontro, que a usa). A 9b começa depois da 1b e roda em paralelo às etapas 2–8 (assets e dados não dependem de contas). A 8 começa junto com a 5 (denúncia e bloqueio básicos já entram na 5). A 9 é curta e fecha antes da 10. A 10 pode ser preparada em paralelo a partir da 6. A 11 depende de todas.

## Verificação (por etapa e final)
- Unitários e API: `./mvnw -q verify`.
- E2E afetados: `./mvnw -q -Pe2e verify -Dit.test=...` no chromium, com a matriz firefox/webkit rodando na CI.
- `AxeIT` estendido a cada tela nova.
- Testcontainers para o banco, uma suíte com Docker por vez.
- Provas de celular num emulador Android (AVD arm64 com Chrome, `scripts/android/`), conduzidas por agentes. Playtests feitos por agentes com roteiro e capturas; ao dono chegam só escolhas (dono, 2026-11-22).
- Final: smoke e2e contra homologação e uma rodada de encontros de teste por agentes antes do piloto.

## Backlog de cenários (ideias do dono, 2026-11-22)
Escolhidos para o piloto (Etapa 9b): aquário, piquenique, cachoeira, jantar e fliperama. Ficam no backlog: planetário (repete as estrelas do terraço) e parque (se sobrepõe ao piquenique). Cada cenário segue o formato do terraço: criação a dois, ~30 min, sem placar, revelação no fim. As atividades abaixo são propostas iniciais, a validar em brainstorming próprio.

| Cenário | Atividade compartilhada proposta |
|---|---|
| Parque | Montar juntos um canto do parque (banco, árvore, pipa, lago) e ver o pôr do sol mudar conforme a composição |
| Cachoeira | Escolher a trilha e o mirante; a combinação decide a luz e o arco-íris na revelação |
| Planetário | Cada um posiciona estrelas e os dois desenham uma constelação nova, batizada no fim |
| Aquário | Compor um tanque (plantas, pedras, peixes); a revelação acende a iluminação e os peixes nadam |
| Jantar | Montar a mesa e escolher pratos a dois; a revelação é o jantar servido com a trilha escolhida |
| Piquenique | Preparar a toalha e a cesta; o clima e os passantes reagem às escolhas |
| Fliperama | Jogo cooperativo original a dois (sem recriar jogos existentes); um recorde conjunto, não competição |
