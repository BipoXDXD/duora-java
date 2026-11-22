# Pesquisa: segurança, moderação e UX mobile para o Duora

Data: 2026-11-22. Comparação com Tinder, Bumble, Hinge, Grindr, Yubo, Discord, Timeleft, apps de voz (Xbox/Roblox como referência de voz) e fornecedores de moderação.

> **Não é parecer jurídico.** Os textos da Lei 15.211/2025 e do Decreto 12.880/2026 foram lidos no Planalto. Marco Civil, LGPD, CP, ECA (Lei 8.069) e a Res. CD/ANPD 2/2022 aparecem citados com o link oficial, mas não foram relidos nesta pesquisa (marcados com †). Itens marcados com "(inferência)" são conclusões minhas a partir das fontes.

## Resumo para decisão

1. **O "18+ por autodeclaração" do spec conflita com a lei.** O ECA Digital está em vigor desde 17/03/2026 e o art. 9º §1º exige "mecanismos confiáveis de verificação de idade [...] vedada a autodeclaração". O Decreto 12.880/2026 (art. 15 §1º X) inclui apps "com finalidade precípua de marcar encontros" entre os serviços proibidos a menores. Isso pede **decisão do dono** (opções na seção 1.1).
2. **Faltam no spec três deveres que valem para qualquer porte:**
   - fluxo de abuso sexual infantil e aliciamento: remover, preservar e reportar à Polícia Federal;
   - processo de notificação com direito de defesa e recurso (tese do STF de 2025);
   - política de guarda de logs e evidências.
3. **A moderação de 1 pessoa cabe em orçamento zero.** A combinação é um classificador de imagem aberto (Falconsai, Apache-2.0) rodando no Spring via ONNX Runtime, o Cloudflare CSAM Scanning Tool (grátis) e uma fila com severidade e SLA no próprio monólito. Serviços pagos (Sightengine, Rekognition) ficam como reserva.

---

## 1. Obrigatório no Brasil em 2026

| # | Obrigação | Base legal | O que o Duora precisa fazer | Situação no spec |
|---|---|---|---|---|
| L1 | **Verificação de idade confiável; autodeclaração vedada** | Lei 15.211/2025 art. 9º caput e §1º; Decreto 12.880/2026 art. 15 caput, §1º X e §2º; art. 19 II ([Lei](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2025/lei/L15211.htm), [Decreto](https://www.planalto.gov.br/ccivil_03/_ato2023-2026/2026/decreto/d12880.htm)) | Mecanismo efetivo no cadastro; impedir a criação de contas por menores; identificar e remover contas de menores | **Conflita** (autodeclaração + moderação) |
| L2 | Método proporcional ao risco, com minimização e sem rastrear identidade; **imagem de documento não pode ser guardada** | Decreto art. 24 I–XI e §3º; direito de contestar o resultado (art. 27) | Guardar só "≥18 / data / fornecedor / resultado"; descartar a imagem; oferecer canal para contestar | Ausente |
| L3 | Canal de denúncia público, acessível e fácil de achar | Lei 15.211 arts. 28 e 29 §3º; STF Temas 987/533 ([STF](https://noticias.stf.jus.br/postsnoticias/stf-define-parametros-para-responsabilizacao-de-plataformas-por-conteudos-de-terceiros), [teses](https://noticias.stf.jus.br/wp-content/uploads/2025/06/MCI_tesesconsensuadas.pdf)) | Denunciar em todas as telas (já previsto) **e** um canal público fora do app (página e e-mail) para quem não tem conta | Parcial |
| L4 | **Abuso sexual infantil e aliciamento:** remover prontamente, preservar material, dados da conta e metadados, reportar à PF e apagar o material após a confirmação | Lei 15.211 art. 27 (§2º remete ao prazo do art. 15 do MCI); Decreto arts. 36–42 (PF como autoridade central, envio automatizado no art. 42); ECA art. 241-A §2º† ([ECA](https://www.planalto.gov.br/ccivil_03/leis/l8069.htm)) | Ação "suspeita de abuso infantil" na fila → oculta na hora → congela a evidência com acesso restrito → reporte à PF (Denúncia Online/SaferNet até o canal automatizado existir; não verifiquei se ele já está no ar) | **Ausente** |
| L5 | Remoção após notificação: ilícitos em geral e contas falsas denunciadas; dever de cuidado contra "falha sistêmica" em crimes graves (incluem crimes contra mulheres e crianças); autorregulação com notificação, devido processo e canais permanentes | STF, RE 1037396 e RE 1057258, julgados em 26/06/2025 ([STF](https://noticias.stf.jus.br/postsnoticias/stf-define-parametros-para-responsabilizacao-de-plataformas-por-conteudos-de-terceiros)) | Avisar o denunciado sobre a decisão e o motivo; **um recurso por decisão**; registrar tudo no audit log | Parcial (sem recurso nem aviso) |
| L6 | Nudez não consentida: remover após notificação da vítima | MCI art. 21†; CP art. 218-C† ([MCI](https://www.planalto.gov.br/ccivil_03/_ato2011-2014/2014/lei/l12965.htm)) | Motivo de denúncia "imagem íntima sem consentimento" com prioridade alta | Coberto pela regra "sem imagens sensíveis"; falta o motivo específico |
| L7 | Guarda de registros de acesso (IP, porta e timestamp) por 6 meses, sob sigilo | MCI art. 15† (literalmente, só pessoa jurídica com fins econômicos); ECA Digital art. 27 §2º remete a esse prazo | Guardar mesmo como pessoa física (boa prática; vira dever se houver CNPJ ou cobrança) | Ausente |
| L8 | LGPD: base legal por finalidade, dado sensível (vida/orientação sexual; biometria se houver selfie) com consentimento destacado, direitos do titular, segurança, **incidente à ANPD e aos titulares em 3 dias úteis** | LGPD arts. 5º II, 11, 18, 46, 48†; Res. CD/ANPD 15/2024 ([Mattos Filho](https://www.mattosfilho.com.br/unico/regulamento-incidente-seguranca/)) | Já previsto: consentimento destacado para gênero/preferência. Faltam o plano de incidente de 1 página e a nomeação do dono como encarregado (contato público) | Parcial |
| L9 | Agente de pequeno porte perde os benefícios (dispensa de encarregado, prazos em dobro) se o tratamento for "de alto risco" (dado sensível + impacto significativo) | Res. CD/ANPD 2/2022† ([DOU](https://www.in.gov.br/en/web/dou/-/resolucao-cd/anpd-n-2-de-27-de-janeiro-de-2022-376562019)) | Tratar como alto risco por precaução: encarregado nomeado e RIPD curto (art. 38) | "LGPD leve" dispensa o RIPD; **revisar** |
| L10 | Avaliação de impacto à segurança de crianças, com resumo público | Decreto art. 47 | Só vale se o serviço for "de acesso provável"; com L1 efetivo dá para argumentar que não é. Uma versão curta é barata (inferência) | Ausente |
| L11 | Acessibilidade em sites de empresas com sede no país | LBI (Lei 13.146/2015) art. 63† ([Planalto](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2015/lei/l13146.htm)) | WCAG 2.2 AA (já é a meta do projeto) | Coberto |

**Não se aplicam ao Duora:**
- relatório de transparência do ECA Digital (só acima de 1 mi de usuários menores; art. 31);
- sinal de idade de loja e sistema operacional, enquanto o app for PWA fora da Play Store; o caminho só existiria empacotado como TWA, e isso é incerto.

**Sanções e fiscalização:**
- Sanções (art. 35): começam por advertência com até 30 dias para corrigir. Sem faturamento, a multa vai de R$10 a R$1.000 por usuário cadastrado, com teto de R$50 mi.
- Cronograma: a fiscalização sistemática da aferição de idade começa em jan/2027 ([Agência Brasil](https://agenciabrasil.ebc.com.br/direitos-humanos/noticia/2026-03/consulta-publica-ouvira-sociedade-sobre-implementacao-do-eca-digital)).
- Guia da ANPD: o guia definitivo de aferição saiu em consulta em 22/05/2026 e, até 22/11/2026, não encontrei a versão final publicada ([ANPD](https://www.gov.br/anpd/pt-br/assuntos/noticias/copy_of_anpd-tomada-de-subsidios-afericao-idade), [ANPD EN](https://www.gov.br/anpd/pt-br/assuntos/eca-digital/eca-digital-english)).

### 1.1 Decisão do dono: como cumprir L1

Pela minuta da ANPD de mai/2026 ([PPC Land](https://ppc.land/brazils-anpd-releases-draft-age-verification-guide-open-to-public-input/), [orientações preliminares](https://www.gov.br/anpd/pt-br/assuntos/eca-digital/mecanismos-confiaveis-de-afericao-de-idade-orientacoes-preliminares.pdf)):
- serviço com interação entre usuários = **risco moderado** (abordagem em camadas);
- serviço proibido a menores = **risco alto** (verificação robusta obrigatória);
- **CPF digitado conta como autodeclaração**;
- estimativa facial é aceita se transmitir só o atributo "≥18", usar liveness e não gerar template biométrico.

Na leitura prudente, o Duora (encontros, Decreto art. 15 §1º X) fica no risco alto.

| Opção | Como | Prós | Contras | Custo |
|---|---|---|---|---|
| **A. Estimativa facial de idade + documento como reserva** | Selfie com liveness num fornecedor que devolve só "≥18"; se a estimativa ficar perto do limite, pede documento (imagem descartada) | Padrão de mercado (Tinder Face Check, Hinge no Reino Unido e na Austrália, Yubo/Yoti); alinhado ao Decreto art. 24; atrito baixo | Biometria = dado sensível (pede RIPD e consentimento); fornecedor externo; ~2% de falsos negativos exigem o caminho do documento | Didit: liveness passivo US$0,10, documento US$0,15; a cota grátis aparece de três formas conflitantes ([Didit](https://docs.didit.me/getting-started/pricing)) |
| B. Só documento | Foto do RG/CNH com face match | Mais robusto | Mais atrito e abandono; risco maior com a imagem | ~US$0,15–0,33 por verificação (Didit) |
| C. Credencial verificável gov.br | Prova "≥18" emitida pelo governo | Preferida pela ANPD (minimização) | Não verifiquei se existe API disponível para app privado em 2026 | Indefinido |
| D. Manter a autodeclaração | Spec atual | Zero atrito | Contraria o art. 9º §1º; alto risco de um menor conversar com adulto num app de encontros | US$0 |

**Recomendação: A.** A verificação entra na Etapa 2, antes do piloto aberto, e o "selo verificado" sai de graça como sinal de confiança (resolve também P1 da seção 2). Isso tira "verificação por selfie" dos não-objetivos do spec.

---

## 2. Paridade de segurança

Lacunas do Duora (spec de 2026-11-21) em relação ao que o mercado já entrega, ordenadas por impacto.

| # | Recurso de referência | Quem faz | Duora hoje | Recomendação |
|---|---|---|---|---|
| P1 | **Verificação de idade e selfie com liveness** | Tinder Face Check, obrigatório para novos usuários nos EUA desde out/2025 ([Match](https://mtch.com/?p=3758), [TechCrunch](https://techcrunch.com/2025/10/22/tinder-will-require-new-users-in-the-us-to-verify-their-identity-with-a-selfie)); Hinge com FaceTec desde fev/2026 ([Biometric Update](https://www.biometricupdate.com/202602/match-group-extends-facetec-liveness-to-hinge-adds-age-verification-for-uk-oz-users)); Yubo virou 18+ com verificação em nov/2025 ([Yubo](https://yubo.live/blog/yubo-is-becoming-an-18-plus-app)) | Autodeclaração; selfie como não-objetivo | Opção A da seção 1.1. Guardar só o resultado, nunca o vídeo nem o template (o Tinder guarda a geometria facial, ver "Não copiar") |
| P2 | **Fluxo de abuso infantil** com hash matching e reporte | Cloudflare CSAM Scanning Tool, grátis e só com e-mail verificado desde fev/2025 ([Cloudflare](https://blog.cloudflare.com/a-simpler-path-to-a-safer-internet-an-update-to-our-csam-scanning-tool/)); ROOST Coop 1.0, Apache-2.0 ([ROOST](https://roost.tools/blog/coop-1-0-world-s-first-free-open-source-child-safety-infrastructure-for-every-platform/)) | Ausente | Ver L4. Se as imagens passarem pela Cloudflare (o spec usa Caddy na VPS), ativar a ferramenta. Ela reporta ao NCMEC (EUA); o reporte brasileiro à PF segue manual |
| P3 | **Pré-triagem automática de imagens** | Bumble Private Detector borra a imagem antes de ela chegar ([Bumble](https://bumble.com/the-buzz/bumble-open-source-private-detector-ai-cyberflashing-dick-pics)) | Imagem chega borrada e só é revista se denunciada; a foto de perfil tem aprovação manual | Classificador no servidor antes da entrega: score alto → bloqueia e explica; zona cinzenta → entrega borrada com aviso reforçado e vai à fila. A foto de perfil com score baixo pode dispensar o admin (reduz fila) |
| P4 | **Evidência de voz para denúncia** | Xbox guarda um clipe de 60 s no aparelho por 24 h e só envia se denunciado ([Xbox Wire](https://news.xbox.com/en-us/2023/07/12/xbox-voice-reporting-feature/)); Yubo transcreve trechos de 10 s via Hive em lives ([TechCrunch](https://techcrunch.com/2022/11/16/yubo-expands-real-time-audio-moderation-technology/amp/)) | "Sem gravação": denúncia de voz chega sem prova | **Decisão do dono.** (a) Manter sem gravação e aceitar "palavra contra palavra"; (b) buffer circular de 60 s só em memória no cliente de quem denuncia, enviado só na denúncia, com aviso na entrada da cena e nos termos. Recomendo (b): custo zero, privacidade alta, mas exige consentimento claro |
| P5 | **Avisos antes e depois de mensagem ofensiva** | Tinder "Are You Sure?" (−10% de linguagem ofensiva) e "Does This Bother You?" (+46% de denúncias) ([Match](https://mtch.com/?p=1691), [Match](https://mtch.com/?p=514)); Hinge "Did This Bother You" e Comment Filter ([Hinge](https://hinge.co/press/hidden-words)) | Ausente | "Quer mesmo enviar?" a partir de uma lista local de termos em pt-BR; "Isso te incomodou?" no destinatário com atalho para denunciar. Custo zero |
| P6 | **Devido processo** (aviso ao denunciado, recurso) | Exigido pela autorregulação do STF; Coop e Ozone têm fluxo de apelação | Ban sem recurso | Aviso com o motivo e a regra violada; 1 recurso por decisão, revisto após 24 h com checklist (só há um moderador) |
| P7 | **Central de segurança** e dicas de encontro presencial | [Yubo Safety Hub](https://yubo.live/safety); Bumble Share Date ([Bumble](https://bumble.com/the-buzz/bumble-dating-features)) | Ausente | Página estática "Segurança" (regras, como denunciar, canal público, dicas para o primeiro encontro presencial), sempre no mesmo lugar (WCAG 3.2.6). "Compartilhar meu encontro" só depois da conexão mútua, no backlog |
| P8 | **Limites para contas novas** contra abuso em série | Prática comum (inferência; Face Check também detecta o mesmo rosto em várias contas, [Investing](https://ng.investing.com/news/company-news/tinder-expands-facial-verification-across-us-to-combat-fake-profiles-93CH-2158045)) | Rate limit só no cadastro e no login | Contas com < 7 dias: no máximo N cenas por dia e imagens desligadas nas 3 primeiras cenas; após uma denúncia confirmada, o hash do e-mail e o resultado da verificação impedem a volta |

**Já em paridade ou acima:**
- sair, denunciar e bloquear visíveis em toda tela;
- bloqueio bilateral silencioso;
- mudo do outro sempre visível e microfone desligado ao entrar;
- links com aviso de saída e sem prévia;
- imagens só com consentimento mútuo, borradas até o toque;
- "parece menor de idade" suspende na hora;
- localização só como célula de ~5 km;
- EXIF removido;
- recusa nunca revelada.

Isso supera o padrão de Tinder e Bumble em privacidade.

---

## 3. Ferramentas de moderação recomendadas (com custo)

Preços consultados em 2026-11-22.

### 3.1 Pilha recomendada (custo marginal ≈ US$0)

| Camada | Ferramenta | Licença / custo | Como encaixa no Duora |
|---|---|---|---|
| Nudez e conteúdo sexual em imagens (servidor) | **Falconsai/nsfw_image_detection** (ViT, ~86 M parâmetros) | Apache-2.0, uso comercial ok ([HF](https://huggingface.co/Falconsai/nsfw_image_detection)) | Exportar para ONNX → **ONNX Runtime Java** (ou DJL) no Spring; redimensionar para 224×224 no mesmo passo que já gera o WebP. Limiar de bloqueio e limiar de fila calibrados com amostras próprias |
| Reserva aberta | Bumble Private Detector | Apache-2.0, TF SavedModel (precisa tf2onnx), parado desde 2022 ([GitHub](https://github.com/bumble-tech/private-detector)) | Segunda opinião na zona cinzenta, se o Falconsai errar muito em pt-BR/selfies |
| Pré-aviso no navegador (opcional) | **NSFWJS** | MIT, MobileNetV2 ~3,5 MB, ~90–93% de acurácia segundo o README ([GitHub](https://github.com/infinitered/nsfwjs)) | Só UX ("esta imagem parece violar as regras") antes do upload. **Nunca** decide: o cliente é manipulável |
| Abuso infantil (hash) | Cloudflare CSAM Scanning Tool | Grátis ([Cloudflare](https://developers.cloudflare.com/changelog/2025-02-04-easier-onboarding-for-csam-scanning-tool)) | Exige que as imagens passem pelo proxy da Cloudflare; disponibilidade fora dos EUA não confirmada |
| Texto (chat) | Lista de termos pt-BR + regras (links, frequência) | US$0 | Alimenta P5 e a prioridade da fila |
| Triagem assistida (fila) | LLM guard self-hosted (ShieldGemma; Llama Guard 4) ou o próprio Claude via API | Termos Gemma / Llama Community (não verificado) | Só **resume e sugere severidade** na fila; ban sempre humano |
| Fila, SLA e auditoria | Módulo `moderacao/` próprio | US$0 | ROOST Coop 1.0 (Apache-2.0, [docs](https://roostorg.github.io/coop/1.0/user/)) e Bluesky Ozone ([GitHub](https://github.com/bluesky-social/ozone/)) servem de **referência de design**; operar o Coop é pesado demais para 1 pessoa (inferência) |

### 3.2 Serviços pagos (reserva ou escala)

| Serviço | Preço | Observação |
|---|---|---|
| Sightengine | Free 2.000 ops/mês (máx. 500/dia); Starter US$29/mês por 10 mil ops; extra US$0,002 ([pricing](https://sightengine.com/pricing)) | A opção paga mais simples para um piloto |
| AWS Rekognition DetectModerationLabels | ~US$0,001/imagem; free tier de 1.000/mês por 12 meses ([AWS](https://aws.amazon.com/rekognition/pricing/)) | ~US$5 por 5 mil imagens/mês |
| AWS Rekognition Face Liveness | US$0,015/checagem, sem free tier ([AWS](https://aws.amazon.com/rekognition/pricing/)) | Alternativa barata ao liveness da Didit, mas sem estimativa de idade embutida |
| Didit | Liveness passivo US$0,10; face match US$0,05; documento US$0,15 ([docs](https://docs.didit.me/getting-started/pricing)) | Recomendado para L1/P1 |
| Hive | Visual US$3/1000 (**exige contrato anual**); texto US$0,50/1000; áudio US$0,03/min ([Hive](https://thehive.ai/pricing)) | Moderar a voz inteira custaria ~US$0,90 por cena de 30 min: inviável |
| Modulate ToxMod | Starter US$0; o primeiro plano pago custa US$5.000/mês ([Modulate](https://www.modulate.ai/toxmod-pricing)) | Fora do orçamento |
| Google Cloud Vision SafeSearch | Historicamente US$1,50/1000 após 1.000 grátis (**não verificado**) | Sem vantagem sobre Rekognition |

**Não usar:**
- **Perspective API:** encerra em 31/12/2026 ([perspectiveapi.com](https://perspectiveapi.com/)).
- **OpenAI omni-moderation:** é grátis, mas os termos restringem o uso a conteúdo das APIs da OpenAI ([OpenAI](https://openai.com/index/upgrading-the-moderation-api-with-our-new-multimodal-moderation-model/)).
- **NudeNet:** os modelos derivam do Ultralytics YOLOv8 (AGPL-3.0); o risco de licença não está resolvido.

### 3.3 Desenho da fila para 1 moderador (inferência a partir de Coop, Ozone, Xbox e das leis)

**Severidade e SLA** (mapeia os SLAs do spec):

| Nível | Casos | Ação imediata | Revisão |
|---|---|---|---|
| P0 | Suspeita de menor, abuso infantil, ameaça | Suspensão automática na hora | ≤ 12 h |
| P1 | Imagem sensível, nudez não consentida, assédio sexual | Oculta automaticamente para quem denunciou | ≤ 24 h |
| P2 | Grosseria, spam, link suspeito | — | ≤ 48 h |

**Fila e operação:**
- A ordenação é por (severidade, `due_at`).
- A fila tem contadores de "vence em".
- O moderador recebe alerta por push quando chega um P0.

**Auto-ação provisória:**
- Dispara com N denúncias independentes ou com score alto do classificador.
- Sempre reversível pelo humano.

**Evidência:**
- Snapshot congelado no momento da denúncia (mensagens denunciadas + 5 de contexto, imagem, clipe de voz se houver P4), com SHA-256, criptografado em repouso.
- Acesso só pelo papel ADMIN, e cada visualização entra no audit log.

**Retenção:**

| Dado | Prazo |
|---|---|
| Evidência de caso | Enquanto o caso estiver aberto + 6 meses |
| Ban permanente | Só o hash de e-mail e de dispositivo, para impedir a volta |
| Material de abuso infantil | Preservado até a confirmação da PF e depois apagado (Decreto art. 39 §2º) |
| Logs de acesso | 6 meses |

**Audit log:**
- Append-only, com quem, quando, ação, motivo, versão da regra e caso.
- Teste: uma tentativa de UPDATE ou DELETE falha.

**Recurso:** um por decisão, com resposta ao usuário.

---

## 4. Padrões de UX/UI a adotar

### 4.1 Onboarding e permissões
- **No máximo 5 telas obrigatórias antes da primeira cena:** e-mail/link mágico, verificação de idade, apelido, intenção (amizade/namoro), regras + consentimento. O resto (foto, bio, preferências finas, voz) é pedido depois, quando destrava valor.
  - A primeira tela é a que mais perde gente; da 3ª em diante, cada tela perde até 1,5% ([Adapty 2026](https://adapty.io/state-of-onboarding/report/)).
  - Encurtar subiu a conclusão de 78,1% para 86,7% num A/B ([RevenueCat](https://www.revenuecat.com/blog/growth/everlove-onboarding-experiment)).
  - Permissões são a principal causa de onboarding longo ([Mobbin](https://mobbin.com/blog/the-long-onboarding-a-study-of-1-460-flows)).
- **Login sem teste cognitivo** (WCAG 3.3.8): link mágico com colar permitido; sem CAPTCHA de quebra-cabeça (o Turnstile invisível cumpre).
- **Timeleft como referência de tom:** "personality, not selfies" ([App Store](https://apps.apple.com/us/app/-/id6466442949)) e revelação gradual. Casa com a foto borrada do Duora.

### 4.2 Microfone e voz
- O pedido do microfone sai do **gesto "Entrar com microfone"** na tela da cena, ao lado de "Entrar só com texto". Sem tela de explicação separada, que a Mozilla trata como anti-padrão ([Mozilla](https://blog.mozilla.org/webrtc/wp-json/wp/v2/posts/489)). Se a permissão for negada, mostrar como reativar e seguir em texto.
- **Teste de dispositivo antes**: no navegador mobile, `getUserMedia` pode ter sucesso com o mic ocupado por outro app ([Twilio](https://twilio.com/blog/best-practices-video-mobile-browsers)).
- **Estados explícitos e anunciados** (`role="status"`), com ícone + texto e nunca só cor: Conectando → Conectado → Conexão fraca (via `getStats`: RTT/perda) → Reconectando (**ICE restart**, [discuss-webrtc](https://groups.google.com/g/discuss-webrtc/c/3jxTy9v8mD4)) → "A voz caiu; continuem por texto".
- Microfone aberto com mudo de um toque como padrão; "segurar para falar" como opção.
- TURN em TLS 443 como reserva (CGNAT das operadoras móveis).
- Avisar que bloquear a tela ou trocar de app pode pausar o áudio ([Janus](https://groups.google.com/g/meetecho-janus/c/Vm6gYSRrkes)).

### 4.3 Notificações, instalação, offline, haptics
- **Notificação:** pedir só depois da primeira cena ou quando a pessoa marca "me avise quando houver alguém". No Chrome 155 para Android (jul/2026) o prompt é leve e expira; nesse caso `requestPermission()` devolve `'default'`. Tratar como "ainda não" e observar `permissions.query(...).onchange` ([Chrome](https://developer.chrome.com/blog/notification-prompts-android)).
- **Etiqueta** (inferência): no máximo 1 push de reengajamento por dia, nada entre 22h e 8h, push de evento real sempre permitido e nenhum push falso.
- **Instalação:** guardar o `beforeinstallprompt` e oferecer depois da primeira cena concluída, com snackbar dispensável + item fixo no menu; esconder com `appinstalled`. Critérios: manifest com ícones de 192 e 512 px, `display` e ≥30 s de uso com 1 toque ([web.dev](https://web.dev/articles/install-criteria), [promote-install](https://web.dev/articles/promote-install)). Adicionar `screenshots` e `description` ao manifest libera a "Richer Install UI".
- **Offline:** o app shell fica em cache-first e os dados em network-first ([web.dev](https://web.dev/learn/pwa/caching)). A cena ao vivo não funciona offline e deve dizer isso em linguagem clara. A decisão privada e as mensagens pendentes vão para uma fila de reenvio (Background Sync no Chromium, com retry manual como reserva).
- **Haptics:** `navigator.vibrate` (só Android, [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Vibration_API)) como reforço curto (conexão mútua, 1 min restante), nunca como único sinal; desligado com `prefers-reduced-motion`.
- **Dark mode** por `prefers-color-scheme`, com superfície mais clara no lugar de sombra (RUI 9). `viewport-fit=cover` + `env(safe-area-inset-*)`.

### 4.4 Acessibilidade (WCAG 2.2 AA) — pontos específicos do Duora
- **2.2.1 Timing Adjustable** (cena de 30 min): a exceção "real-time" provavelmente cobre a cena, mas o caminho seguro é:
  - timer visível;
  - anúncio `aria-live="polite"` aos 5 min e a 1 min, sem anunciar cada segundo;
  - extensão por consentimento mútuo;
  - **sem cronômetro na decisão privada** ([W3C](https://www.w3.org/WAI/WCAG22/Understanding/timing-adjustable.html)).
- **4.1.3:** o chat em `role="log"` e os estados de chamada e fila em `role="status"`.
- **2.4.11:** o teclado virtual do Android não pode cobrir o campo do chat. Usar `interactive-widget=resizes-content` ou a VisualViewport API.
- **2.5.7 / 2.5.8:** nada só por arrasto nos cenários (cada peça arrastável também pode ser tocada → destino). Alvos de 44 px (regra RUI 11), mínimo de 24 px.
- **3.2.6:** "Ajuda e segurança" sempre no mesmo lugar.
- Testar com TalkBack no PWA **instalado** (standalone), não só na aba.

### 4.5 Visual (o que os usuários elogiam em 2025–2026)
- **Perfis por personalidade, não por swipe:**
  - prompts e Voice Prompts de 30 s do Hinge ([Hinge](https://hinge.co/press/voiceprompts), [2025](https://hinge.co/newsroom/hinge-2025-product-evolution));
  - Bumble "saying goodbye to the swipe" com perfil em capítulos ([Axios 2026](https://axios.com/2026/05/11/bumble-reset-gen-z-dating-apps));
  - Tinder Modes com blur sutil e fotos de borda a borda ([TechCrunch](https://techcrunch.com/2025/09/10/tinder-evolves-some-features-into-dating-modes/)).
- **Para o Duora:**
  - a foto borrada como teaser, com a revelação ligada ao consentimento;
  - os prompts da cena como cartões grandes e tocáveis;
  - modo amizade/namoro explícito desde a entrada.
- **Material 3 Expressive** (mai/2025): CTA principal grande e de alto contraste, formas e movimento com função. O Google relata achar elementos-chave até 4x mais rápido, mas o estudo é da própria empresa ([Google Design](https://design.google/library/expressive-material-design-google-research)).
- Aplicar as regras RUI do projeto:
  - escala fixa de espaço, tipo e cor;
  - pesos 400/600;
  - botões em pirâmide, com "Denunciar" rebaixado mas visível e vermelho só na confirmação;
  - todos os estados desenhados;
  - estado vazio honesto ("ninguém compatível agora; te avisamos") com uma única CTA e sem falsa urgência.

---

## 5. Metas mensuráveis

| Área | Meta | Como medir | Origem |
|---|---|---|---|
| Onboarding | ≤ 5 telas obrigatórias até a primeira cena; mediana ≤ 2 min, **incluindo** a verificação de idade | E2E conta telas; métrica interna de tempo | Inferência (Adapty/RevenueCat) |
| Onboarding | 0 pedidos de permissão no primeiro acesso | E2E afirma que nenhum prompt abre antes da ação | Chrome/Android docs |
| Performance | LCP ≤ 2,5 s, INP ≤ 200 ms, CLS ≤ 0,1 (p75) | Lighthouse CI mobile, CPU 4x, "Slow 4G" | [web.dev Vitals](https://web.dev/articles/vitals) |
| Performance | JS inicial ≤ 170 KB gz; cena, WebRTC e jogo sob demanda | Budget no build (Vite) que falha o CI | [web.dev budgets](https://web.dev/performance-budgets-101) |
| Performance | Fliperama abre em ≤ 5 s num Android médio em 4G com cache frio | Já está no spec | Spec |
| Acessibilidade | axe: 0 violações em todas as telas; contraste 4,5:1 / 3:1 nos dois temas | `AxeIT` + `ContrasteTest` (já existem) | WCAG 2.2 AA |
| Acessibilidade | Alvos ≥ 44 px (mínimo absoluto 24 px); timer anunciado aos 5 e a 1 min; chat em `role="log"` | Teste E2E de geometria + snapshot ARIA | WCAG 2.5.8, 2.2.1, 4.1.3 |
| Voz | Conectado em ≤ 3 s p75 após aceitar; reconexão por ICE restart em ≤ 10 s; queda → texto sem perder a cena | Métrica interna (`getStats`) + teste com rede degradada | Inferência |
| Segurança | Sair, denunciar e bloquear a ≤ 2 toques de qualquer tela; denúncia completa em ≤ 3 toques | E2E | Inferência (Tinder/Hinge) |
| Moderação | P0 suspenso em < 1 min (automático) e revisto em ≤ 12 h; P1 ≤ 24 h; P2 ≤ 48 h; ≥ 95% dentro do SLA | Painel admin com relógio de SLA | Spec + seção 3.3 |
| Moderação | Classificador de imagem: taxa de bloqueio indevido medida por amostragem semanal (meta < 2%) | 20 decisões revistas por semana | Inferência |
| Legal | Verificação de idade em 100% das contas antes da 1ª cena; incidente comunicado em ≤ 3 dias úteis; logs de acesso por 6 meses | Teste de integração + runbook | L1, L7, L8 |

---

## 6. Não copiar

**Recursos e comportamentos de outros apps:**

| O que | Onde aparece | Por quê |
|---|---|---|
| **Segurança atrás de paywall** (bloquear, denunciar, sair, borrar, verificar) | Prática criticada no setor | Todo recurso de segurança é grátis no Duora |
| "Veja quem curtiu você" pago, streaks, contagem regressiva falsa, confirmshaming, opções pré-marcadas, cancelamento difícil | Acordo FTC × Match de US$14 mi em ago/2025 ([Fortune](https://fortune.com/2025/08/13/match-group-ftc-14-million-settlement-deceptive-advertising/)); DSA art. 25 e Digital Fairness Act em preparo ([GDI](https://www.globaldatinginsights.com/news/eu-digital-fairness-act-dating-apps-ai-chatbots/)) | A decisão privada mútua do Duora já evita a maior parte |
| Marketing que sugere "garantia de encontros" | Procon notificou o Tinder em 2024 ([Canaltech](https://canaltech.com.br/apps/apos-pagar-tinder-por-4-anos-homem-aciona-procon-por-nao-marcar-encontros/)); CDC† | Risco de publicidade enganosa |
| Guardar a geometria facial sem permitir apagar sem excluir a conta | Tinder ([Bitdefender](https://www.bitdefender.com/en-gb/blog/hotforsecurity/tinder-face-check-us-states-what-it-means-for-you)) | Conflita com o Decreto art. 24 VIII e a LGPD |
| Ler o rolo da câmera para "match por IA" | Tinder Chemistry ([TechCrunch](https://techcrunch.com/2025/09/10/tinder-evolves-some-features-into-dating-modes/)) | Excesso de dados sensíveis |
| Gravar ou transcrever toda a voz | Yubo/Hive | Caro (~US$0,90 por cena) e invasivo; usar P4 (b) |
| Prometer "bloqueio de print" | Grindr nativo | Impossível num PWA |
| Swipe como única interação | Apps de swipe | Fere WCAG 2.5.7 e contraria a proposta do Duora |
| Pedir notificação ou microfone ao abrir o app; tela de explicação separada | — | Ver 4.2 e 4.3 |

**Atalhos técnicos e de compliance:**

| O que | Por quê |
|---|---|
| CPF digitado como verificação de idade | Para a ANPD equivale a autodeclaração |
| Guardar a imagem do documento | Proibido pelo Decreto art. 24 §3º |
| NudeNet (licença), Perspective API (encerra), OpenAI moderation (termos) | Ver 3.2 |
| Decisão de moderação só no cliente (NSFWJS) | O cliente é manipulável; decidir no servidor |

---

## Lacunas desta pesquisa
- **Guia da ANPD:** o definitivo de aferição de idade não foi localizado. Pode ter saído depois da consulta, o que exige recheck antes da Etapa 2.
- **Fontes não relidas (†):** Marco Civil, LGPD, ECA, CP e Res. 2/2022 vêm de conhecimento prévio.
- **Mensageria privada:** a exceção do STF não foi confirmada no acórdão. Ela pode afetar o regime do chat 1:1.
- **Canal da PF e gov.br:** não verifiquei se o canal automatizado da PF (Decreto art. 42) está operacional, nem se a credencial gov.br está disponível para apps privados.
- **Preços de fornecedores:** faltam Persona, Veriff, Yoti, FaceTec, Azure e Google Vision. A cota grátis da Didit tem descrições conflitantes.
- **Benchmarks não pesquisados:** acurácia independente dos modelos abertos, telas de onboarding de Hinge, Bumble e Tinder, indicadores de conexão de Discord e Meet, BeReal, Partiful, Locket.
