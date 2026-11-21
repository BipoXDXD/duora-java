# Duora Java — uma boa experiência antes de uma plataforma

Proposta revisada em 20/11/2026. Escopo: demonstração local com dados fictícios. Este documento substitui a proposta anterior de “Nossa pequena aventura”. Não altera os requisitos, ADRs, gates ou a produção do Duora C#.

## 1. O que estamos tentando provar

Duas pessoas devem conseguir criar algo juntas em uma experiência curta, bonita e fácil de entender. Ao terminar, o dono precisa poder dizer: “eu gostaria de fazer isso com alguém”. Mais endpoints, documentos ou testes verdes não demonstram, sozinhos, que a experiência é boa.

A primeira entrega completa será **um único ambiente, uma única atividade e duas pessoas fictícias**, em dois contextos isolados de navegador no mesmo computador. Não é uma migração integral nem um aplicativo pronto para receber público.

**Hipótese de produto:** uma pequena criação compartilhada pode estimular curiosidade e colaboração sem questionário pessoal, ranking de compatibilidade ou pressão para responder “certo”. Essa hipótese precisa ser experimentada, não tratada como fato.

## 2. Preservar o que existe e organizar o novo

- Referência: o projeto Duora em C# (somente leitura).
- Destino: este repositório.
- No Plan Mode: não criar pastas, instalar, editar, mudar branches, gerar assets ou disparar serviços.
- Após aprovação de implementação: conferir se o destino existe e preservar qualquer conteúdo. Criar o projeto Java na pasta própria, com histórico independente; nunca copiar o clone, `.git`, bancos, ambientes ou secrets do C#.
- Não usar worktrees, clones históricos, Documents/Codex ou Documents/Claude como destino.
- Não portar o frontend antigo por padrão. Consultar comportamentos e componentes pontuais apenas quando houver benefício comprovado.
- As regras de preservação e segurança continuam valendo. O escopo de produto antigo não vira backlog automático do novo.
- Nenhum commit, push, PR, merge, deploy, operação na VPS ou mudança na assinatura de ferramenta está implícito na fase de planejamento.

## 3. Atividade proposta: “Antes da primeira estrela”

**Premissa:** vocês encontraram um terraço e vão prepará-lo para assistir a uma chuva de meteoros. O resultado é um pequeno diorama montado pelo par. Duração desejada: cerca de 4–6 minutos, sem cronômetro obrigatório. É uma atividade de composição cooperativa, não um puzzle difícil nem um simulador.

### Mecânica fechada para o protótipo

Há seis posições discretas no terraço, sem física ou movimentação livre. Cada participante pode colocar dois objetos, totalizando quatro.

- A recebe telescópio, manta e rádio.
- B recebe banco, lanterna e garrafa térmica.
- Telescópio e banco devem estar presentes para começar a observação.
- Os dois lugares centrais são a área de observação; a lanterna, se escolhida, deve ficar em um dos quatro lugares laterais. A interface explica isso visualmente, sem exigir tentativa e erro.
- Todos veem os objetos disponíveis e a composição. Não há cartas secretas nem inferências sobre preferências pessoais.
- Cada pessoa só modifica seus objetos. Uma posição comporta um objeto. Pode mover ou trocar sua própria peça até a confirmação final.
- Selecionar objeto e selecionar posição é suficiente. Drag-and-drop não é necessário; teclado e toque precisam funcionar.
- Depois da composição válida, cada pessoa confirma “Pronto para olhar o céu”. Uma alteração na composição invalida as duas confirmações; ambos aprovam a versão atual.
- As duas confirmações na mesma versão iniciam uma breve revelação do céu. A cena final mantém exatamente os objetos e posições escolhidos. Sem score, perdedor ou julgamento da relação.

### Três momentos

| Momento | Ação | Retorno visível |
|---|---|---|
| Abrir o terraço | A cria sala, B entra pelo convite; cada um coloca uma peça | O cenário ganha os primeiros objetos e mostra quem fez cada escolha |
| Montar juntos | Cada um coloca a segunda peça; ambos podem reorganizar as próprias peças | Quatro objetos, composição compartilhada e indicação clara do que falta |
| Olhar o céu | Ambos confirmam a mesma composição válida | A iluminação muda, meteoros aparecem brevemente e surge o resultado do par |

A transição entre colocação da primeira e da segunda peça ocorre quando ambos concluíram a primeira; não exigir um clique de confirmação para cada gesto.

Disponibilizar um sinal simples “Que tal mover este?” ao selecionar uma peça do outro participante. Só indica a peça; não a move. Esse é o único recurso extra de comunicação da demonstração. Sem chat, texto livre adicional ou voz ao vivo. A atividade deve funcionar sem uma chamada externa.

### O que avaliar no playtest

- A primeira ação é compreensível sem explicação verbal longa?
- Cada pessoa percebe a contribuição da outra?
- Existe alguma negociação ou curiosidade, ou só uma sequência mecânica de cliques?
- O resultado parece consequência das escolhas?
- Há vontade de experimentar outra composição?

Se a atividade parecer tediosa, ajustar a mecânica dentro deste escopo antes de acrescentar contas, catálogo ou outros jogos. “Bonito” e “divertido” dependem de avaliação humana; o agente não deve autodeclarar aprovação do dono.

## 4. Direção visual: terraço de observatório em miniatura

**Direção única proposta:** diorama noturno com materiais táteis, perspectiva fixa e iluminação quente dos objetos contra um céu azul profundo. Elegante e acolhedor, com proporções legíveis. Sem avatares humanos nesta etapa: evita custo de animação e rostos inconsistentes.

A cena ocupa aproximadamente dois terços da área útil em desktop. A interface acompanha a cena: nomes discretos, instrução curta, objetos disponíveis e uma ação principal contextual. Não colocar o jogo dentro de um dashboard de cards.

### Base visual enxuta

| Token | Uso proposto |
|---|---|
| Azul noite `#132B40` | Fundo e estrutura |
| Azul céu `#345E78` | Profundidade e regiões do cenário |
| Marfim `#F5F1E7` | Texto principal e superfícies de leitura |
| Âmbar `#F2BD70` | Luz e ação principal, com texto escuro |
| Verde água `#A8D5C5` | Confirmação e participação |
| Coral `#F09A91` | Erro acompanhado de texto/ícone |

Paleta inicial, não certificação de contraste: medir as combinações reais. Usar Atkinson Hyperlegible para controles e texto; Fraunces apenas no título da experiência. Servir fontes locais com licença conferida; manter fallbacks de sistema.

Os lugares de posicionamento surgem apenas durante a seleção, integrados ao piso. Usar imagem/foco/legenda para identificar objetos; nunca depender só de cor.

Desktop: cena à esquerda, painel compacto de ações à direita. Mobile: cena acima, controles abaixo, sem rolagem horizontal e sem esconder “Sair”. O mesmo estado deve ser compreensível em 390 px e 1440 px de largura.

### Movimento e som

- Resposta imediata ao selecionar, com estado de envio explícito; exibir mudança compartilhada após aceite do servidor.
- Transições curtas de entrada de objeto e iluminação. Sem câmera livre, efeitos contínuos ou partículas em toda a interface.
- Meteoros somente no final; versão estática equivalente para reduced motion.
- Som começa desligado. Um controle permite ativar ambiente e efeitos; tudo funciona no silêncio.
- Narração é dispensável. Se usada, curta, com transcrição e opção de pular.

### Rejeitar antes de integrar

Assets com perspectiva ou iluminação incompatíveis, aparência de clipart, texto ilegível em imagem, deformações, halos em recortes, objetos indistinguíveis no celular, contraste ruim e telas dominadas por painéis genéricos.

Não começar construindo landing page, branding completo, design system, loja ou painel administrativo. A tela principal da atividade é a primeira peça visual a demonstrar.

## 5. Usar as ferramentas disponíveis com intenção

As assinaturas foram informadas pelo dono; permissões, créditos, recursos e acesso por MCP/API ainda precisam de conferência. Não é obrigatório usar todas.

| Ferramenta | Papel nesta entrega |
|---|---|
| Higgsfield | Estudos do cenário e iluminação; selecionar uma imagem principal coerente, sem vídeo obrigatório |
| Adobe Creative Cloud Pro | Composição, correção de cor/perspectiva, recorte, ícones simples e otimização dos arquivos |
| Rodin Business | Opção para gerar/editar props 3D e preparar referências visuais; conferir recursos de imagem e edição disponíveis na conta |
| Hi3D | Alternativa para geração de props pela assinatura/OAuth; escolher por resultado e acesso, sem duplicar gerações por rotina |
| Blender local | Inspecionar, ajustar materiais, escala e câmera; renderizar props com o mesmo enquadramento e iluminação |
| ElevenLabs | Um ambiente sonoro discreto e poucos efeitos pré-gerados; narração opcional |

**Limite inicial de produção:** um fundo, seis props, uma pequena camada de meteoros, ícones de interação e, opcionalmente, um loop ambiente e dois efeitos. Os seis props devem compartilhar escala, câmera e estilo. Usar apenas duas ou três variantes de estudo para escolher a direção, sem uma fila de gerações sem fim.

A página usa renders 2D e camadas HTML/CSS. Modelos 3D são material de produção dos renders; não entram no runtime. Sem Three.js, engine, física, streaming ou geração de IA durante a sessão.

### Pipeline curto de assets

1. Definir composição e câmera num esboço simples.
2. Produzir um fundo e dois props de referência; validar coerência antes dos demais.
3. Usar a mesma referência nas gerações restantes e normalizar em Adobe/Blender.
4. Exportar assets otimizados e integrar sobre a cena interativa.
5. Conferir screenshots desktop/mobile e corrigir as diferenças mais visíveis.

Na implementação nova, manter masters necessários em `assets/source/`, exports usados pela aplicação em `src/main/resources/static/assets/` e um único `assets/manifest.md` com origem, licença, ferramenta e arquivo derivado. Master e export têm funções distintas; descartar tentativas sem utilidade, sem duplicar bibliotecas inteiras.

Meta inicial: downloads essenciais da primeira tela abaixo de 3 MB, com áudio carregado só por solicitação. É um orçamento de projeto a medir, não uma alegação de desempenho já obtido.

Não confundir assinatura com API incluída. Usar somente créditos já autorizados e disponíveis; pedir autorização específica antes de custo adicional. No Plan Mode não consumir créditos. Se uma ferramenta estiver indisponível, manter placeholders identificados e um brief para geração manual, sem inventar execução ou migrar automaticamente para API paga.

Para Hi3D, respeitar a preferência do dono: geração comum pelo MCP OAuth, inclusive Master quando solicitado; API paga só para recurso exclusivo necessário e autorizado. Edições locais por Blender começam por verificar a instalação/status; não exigem autenticar uma conta de API.

## 6. Arquitetura mínima e suficiente

**Escolha proposta:** Java 25 LTS em uma distribuição OpenJDK apropriada, Spring Boot estável compatível, Maven Wrapper e um módulo. Conferir versões/patches atuais e SDK local antes de instalar; sem previews.

HTML, CSS e JavaScript em módulos nativos, servidos pelo Spring Boot. A interação proposta não exige React; reavaliar apenas se a manutenção real justificar, sem portar o frontend antigo. Pequeno arquivo Node de ferramentas só se necessário para Playwright, sem servidor frontend adicional.

Estado em memória e um processo local. Sem banco, Docker, Redis, broker ou serviços pagos. Reiniciar apaga as salas; essa limitação deve ser visível. Não equivale a substituir PostgreSQL nos testes do Duora atual.

Estrutura indicativa, não obrigação de criar arquivos vazios:

```text
Duora/
  pom.xml, mvnw, mvnw.cmd, .mvn/
  README.md
  src/main/java/.../room/          # API, sessão e estado da sala
  src/main/java/.../game/          # regras da composição
  src/main/resources/static/      # index, CSS, JS e assets
  src/test/java/                  # regras e integração HTTP
  tests/e2e/                      # jornadas de navegador, se automatizadas em Playwright
  assets/source/, assets/manifest.md
```

### Estado e sincronização

Estados suficientes: esperando par, primeira peça, segunda peça, composição, finalizado, encerrado. Falta de conexão é um estado da interface, não uma expulsão imediata.

- Identidade temporária por sessão, separada do convite, em cookie HttpOnly/SameSite; credencial não fica na URL, log ou resposta de outro participante.
- Convite permite ocupar somente uma vaga livre; não assumir pessoa existente. Renovar a sessão ao entrar, sem aceitar identidade enviada pelo cliente.
- Dois contextos de navegador independentes para testar A e B; duas abas do mesmo perfil compartilham cookies e não representam duas pessoas.
- API de mesma origem, mutações com proteção CSRF; CORS não permissivo. Bind apenas em loopback.
- Polling do estado a cada cerca de 2 s, sem sobreposição, com backoff em erro; parar ao sair/finalizar. Não prometer tempo real.
- Lock por sala e versão de estado para mutações: requisição obsoleta retorna conflito e recupera a cena atual. Retry não duplica operação.
- Apenas dono modifica sua peça; validar posição, inventário, fase e lotação no servidor. Confirmação final vinculada à versão da composição.
- Refresh recupera a sessão ativa. “Sair” encerra a sala para ambos. Desconexão transitória conserva o estado até expirar.
- Limite proposto de 20 salas e expiração após 30 minutos sem ação válida; polling não renova a expiração. Uma rotina de limpeza, sem thread por sala.

Planejar apenas endpoints de criar/entrar/consultar sala, executar ação, sinalizar peça, confirmar e sair. Não criar uma API genérica de jogos, editor de cenários ou catálogo.

## 7. Quatro incrementos, cada um com uma prova

| Incremento | Entrega que o dono pode ver | Critério para avançar |
|---|---|---|
| 1. Cena com direção visual | Tela principal responsiva, dois props de referência e interação local de selecionar/posicionar | Composição legível e coerente; desktop/mobile conferidos; direção visual apresentada ao dono |
| 2. Atividade completa local | Todo o fluxo com alternância explícita A/B apenas na demonstração local, seis props e final | Existem escolhas e resultado concreto; teste da mecânica sem implementar contas ou rede |
| 3. Sala de verdade | Duas sessões isoladas sincronizadas, credenciais temporárias, concorrência e expiração | Cada participante tem autoridade própria; sem botão público para assumir A/B; jornadas completas |
| 4. Acabamento e validação | Assets finais, acessibilidade, áudio opcional, testes e instruções curtas | Revisão técnica independente e experiência apresentada para avaliação humana |

O seletor A/B do incremento 2 é ferramenta de demonstração, não autenticação. Removê-lo da interface compartilhada do incremento 3 e manter fixtures de teste separadas.

O dono avalia direção visual e atividade em momentos concretos. Se estiver ausente, preparar a evidência e avançar apenas no que não dependa desse aceite; não ampliar escopo nem afirmar que o design foi aprovado.

Não estimar a plataforma inteira antes desses incrementos. Medir esforço e duração da partida no protótipo. Não criar dezenas de issues, ADRs e relatórios.

## 8. Qualidade que protege a experiência

### Funcional e segurança

- Duas sessões completam a atividade e recebem a mesma composição final.
- Não é possível entrar como terceira pessoa, assumir o parceiro, mover a peça dele ou enviar ações após encerramento.
- Duplo clique, retry, ocupação simultânea e confirmação concorrente não duplicam nem corrompem estado.
- Alteração da composição invalida confirmações anteriores.
- Refresh e desconexão temporária recuperam a sala; reinício e expiração explicam o ocorrido.
- Apelidos têm limite e são tratados como texto; credenciais não vazam em logs ou respostas.
- Polling e limpeza terminam corretamente; abrir/sair repetidamente não acumula timers ou processos.

### Visual e experiência

- Um observador identifica onde está, de quem é a ação e como sair.
- Layout sem cortes em 390 px e 1440 px; objetos distinguíveis e controles utilizáveis por toque.
- Teclado completo, foco visível, mensagens acessíveis, contraste medido e reduced motion.
- Tela inicial, colocação, espera, erro e final capturados em Chromium.
- Resultado varia com a composição; ausência de áudio não impede nenhuma ação.
- Placeholders não são apresentados como arte final.

Usar JUnit/Spring Boot Test para regras e API, incluindo concorrência, e Playwright/Chromium com dois contextos para a jornada integrada. Sem meta arbitrária de linhas ou cobertura; cobrir riscos reais. Nenhum teste pulado ou assertion enfraquecida para passar.

Uma revisão técnica independente deve conferir o diff e as evidências antes de declarar a entrega concluída. Ela não substitui o aceite estético do dono. Relatório final curto: o que funciona, comandos/resultados, limitações e como experimentar.

Uma tarefa local pesada por vez entre todos os projetos. Conferir RAM, swap, Docker e processos próprios. Não iniciar watchers permanentes nem encerrar processos do usuário ou de outras sessões.

## 9. Depois da demonstração, só se valer a pena

1. Aprovar ou ajustar a atividade, sem criar outro minijogo para fugir do problema.
2. Planejar contas, persistência PostgreSQL e convites.
3. Planejar decisão privada, conexão mútua, conversa, bloqueio, denúncia e exclusão.
4. Somente então preparar piloto privado com proteção de dados, backup/restauração e operação verificável.

Selfies, voz, 3D em tempo real, catálogo e matching sofisticado não entram por inércia. Também não haverá infraestrutura pública na primeira entrega. A demonstração com dados fictícios não deve receber usuários reais.

## 10. Saída esperada do Plan Mode

Responder com no máximo cerca de 1.200 palavras: decisões finais, wireframe simples de desktop/mobile, regras sem ambiguidades, versões verificadas, poucos endpoints/arquivos, quatro incrementos e testes de aceite. Indicar a primeira ação de implementação após aprovação.

Pode corrigir incoerências deste plano e cortar complexidade; explique o ajuste. Não trocar silenciosamente a mecânica por questionário, landing page ou template de dashboard. Não gerar outro tratado nem executar ferramentas criativas durante o planejamento.

## Fontes consultadas para capacidades e stack

Conferidas em 20/11/2026; versões e disponibilidade devem ser revalidadas antes da execução. Consultas públicas não verificam saldo ou permissões das contas.

- [Java LTS](https://www.oracle.com/java/technologies/java-se-support-roadmap.html) e [requisitos do Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html).
- [Higgsfield: geração de imagens e vídeos](https://higgsfield.ai/creator-hub/help-center/getting-started/how-do-i-create-my-first-generation).
- [Rodin: geração a partir de imagens](https://hyper3d.ai/help/articles/create-3d-models-from-images-with-rodin) e [edição de modelos](https://hyper3d.ai/help/articles/edit-3d-models-in-rodin-workspace).
- [Adobe: créditos generativos](https://helpx.adobe.com/creative-cloud/apps/generative-ai/generative-credits-faq.html).
- [ElevenLabs: efeitos](https://elevenlabs.io/docs/overview/capabilities/sound-effects) e [narração](https://elevenlabs.io/docs/overview/capabilities/text-to-speech).
