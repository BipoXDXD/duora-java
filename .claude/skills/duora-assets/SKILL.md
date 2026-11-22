---
name: duora-assets
description: Use ao gerar, trocar, retocar ou reexportar qualquer asset visual ou sonoro do Duora (render de prop ou terraço, céu, ícone, imagem 2D, modelo 3D, trilha, ambiência ou efeito sonoro), inclusive pedidos como "deixe a lanterna mais quente", "troque o céu" ou "gere um som novo".
---

# Assets do Duora

## Visão geral

Todo asset da página sai de um master versionado (ou baixável) por um pipeline local reprodutível, num formato único por tipo, e fica registrado em `assets/manifest.md`. Ajuste de cor, luz, enquadramento ou mixagem se faz **localmente**, sem gastar crédito de nuvem.

Estas regras valem para este projeto e vencem as regras genéricas de geração (inclusive "imagens 2D pelo Hi3D").

## Formatos (sem exceção por fonte)

| Tipo | Formato do export | Onde |
|---|---|---|
| Imagem (render, céu, recorte) | **WebP qualidade 88** (nunca PNG, JPEG ou WebP com qualidade padrão) | `src/main/resources/static/assets/cenarios/<id>/{cena,ceu}/` |
| Áudio | **MP3 192 kbps, 48 kHz** (os efeitos do ElevenLabs chegam em 44,1 kHz / 128 kbps: sempre passe pelo `ffmpeg`) | `src/main/resources/static/assets/cenarios/<id>/audio/` |
| Vetor | SVG desenhado à mão | `src/main/resources/static/assets/` |

## Fontes de geração

| Tipo | Ferramenta | Observação |
|---|---|---|
| Modelo 3D | MCP `Hi3D` (assinatura/OAuth) | GLB baixado por `get_task_outputs` para `assets/source/modelos/<nome>.glb` (fora do Git) |
| Imagem 2D | MCP `tripo`, modelo `chat_image_2.5_sunburst`, 4K | Master em `assets/source/<tema>/`; a Tripo cobra créditos: rode `tripo_plan` e espere o aceite do dono |
| Áudio | MCP do ElevenLabs | Brutos em `assets/source/audio/brutos/`; só o bruto escolhido entra no Git |

Fallback pago (Rodin Business ou Tripo para 3D) só se o Hi3D falhar ou não tiver o recurso: informe o impedimento e o custo **antes** de gerar.

## Decisão: local ou nuvem?

Gere na nuvem **só** quando o objeto ou som novo não existe em nenhum master. Mudou cor, temperatura, brilho, intensidade de luz, ângulo, recorte, tamanho, volume, fade ou duração? É edição local:

- Render 3D: edite `assets/source/blender/cena.py` (materiais, luzes, `_GIROS_PADRAO`, `_MEDIDAS`) e rerenderize.
- Céu: edite o master 4K localmente e reexporte com `assets/source/ceu/exportar.sh`.
- Áudio: ajuste `assets/source/audio/montar.sh` e rode de novo.
- Efeito que vive no CSS (ex.: `.halo` e `.brilho` da lanterna à noite): confira `frontend/src/estilos/cena.css` antes de mexer no render; o brilho percebido soma render + CSS.

Nunca retoque o `.webp` ou `.mp3` exportado direto (PIL, editor de imagem): o export tem de sair do master pelo pipeline, senão a próxima regeneração desfaz o retoque.

## Pipeline de render (Blender headless, sem créditos)

1. **Confira os GLBs.** `ls assets/source/modelos/<nome>.glb`. Eles são ignorados pelo Git, então um worktree novo **não** os tem. Sem o GLB, o `cena.py` volta em silêncio ao prop procedural low-poly e sobrescreve o render do Hi3D. Se faltar, copie do checkout principal ou baixe de novo pelo `get_task_outputs` do Hi3D com o ID da tarefa 3D listado no manifesto (download não gera tarefa nova).
2. **Edite** `cena.py`.
3. **Renderize** só o que mudou, da raiz do repositório:
   ```sh
   DUORA_SO=lanterna /Applications/Blender.app/Contents/MacOS/Blender -b -P assets/source/blender/cena.py -- "$PWD"
   ```
   Sem `DUORA_SO`, renderiza tudo. `DUORA_AMOSTRAS=32` acelera as iterações; o render final usa o padrão (96). A saída precisa terminar com `DUORA_OK`.
4. **Confira** que os dois momentos saíram nas três larguras (`<prop>-crepusculo-{900,1800,2700}.webp` e `<prop>-noite-{900,1800,2700}.webp`) e que `cena/cena.json` mudou junto: o render parcial atualiza as medidas do prop, e o JSON entra no mesmo commit.
5. **Olhe** o resultado (abra o `.webp`) antes de declarar pronto.
6. **Mudou asset em `static/`:** o `sw.js` é gerado no build do front (`pluginServiceWorker`), com o `CACHE_VERSION` tirado da impressão dos arquivos servidos. Rode `./mvnw -q verify`: o `ServiceWorkerVersaoTest` confere que a versão acompanhou o asset novo. Um asset novo que deva abrir offline também entra em `assetsDoShell` no `frontend/vite.config.ts`.

## Verificação do formato

```sh
file src/main/resources/static/assets/cenarios/terraco/cena/lanterna-noite-1800.webp # RIFF ... Web/P image
ffprobe -v error -show_entries stream=codec_name,sample_rate,bit_rate -of csv=p=0 \
  src/main/resources/static/assets/cenarios/terraco/audio/meteoro.mp3                 # mp3,48000,192000
```

## Manifesto (obrigatório)

Todo asset novo ou trocado ganha ou atualiza sua linha em `assets/manifest.md` **no mesmo commit** do export. A linha traz:

- **fonte:** ferramenta e modelo (ex.: `Hi3D imagem → 3D v3.0`, `Tripo chat_image_2.5_sunburst`, `eleven_text_to_sound_v3`, ou `cena.py` para edição local);
- **task id** de cada geração na nuvem (ou "—" quando só houve edição local, citando a tarefa original do master);
- **data** (AAAA-MM-DD);
- **licença:** `CC BY 4.0 (gerado pelo autor no <serviço>)` para gerações do Hi3D, Tripo ou ElevenLabs (ver `LICENSE-ASSETS`); `Própria` para itens procedurais ou feitos em código; `SIL OFL 1.1` para fontes;
- **status:** `PROVISÓRIO` ou `Candidato a FINAL`. Só o dono promove a `FINAL`, depois do aceite visual ou sonoro.

Edição local também muda a linha: descreva o ajuste (ex.: "luz da vela mais quente no `cena.py`") e a data.

## Erros comuns

| Erro | Correção |
|---|---|
| Gerar outra lanterna no Hi3D ou Tripo para mudar a cor | Edite material e luz no `cena.py` e rerenderize |
| Renderizar num worktree sem os GLBs | Passo 1 do pipeline: copie ou baixe os GLBs antes |
| Exportar PNG, JPEG ou WebP com qualidade padrão | WebP qualidade 88, sempre |
| Usar o MP3 do ElevenLabs como veio (44,1 kHz / 128 kbps) | Passe por `montar.sh` / `ffmpeg -ar 48000 -b:a 192k` |
| Gerar imagem 2D no Hi3D | 2D é no Tripo `chat_image_2.5_sunburst` 4K |
| Esquecer o `cena.json`, um dos momentos ou uma largura | Commit leva `<prop>-crepusculo-<largura>.webp` e `<prop>-noite-<largura>.webp` nas três larguras, e o `cena.json` |
| Editar o `CACHE_VERSION` do `sw.js` à mão depois de trocar asset em `static/` | O `CACHE_VERSION` vem do build; rode o `ServiceWorkerVersaoTest` (passo 6) |
| Pular o manifesto ou marcar `FINAL` por conta própria | Linha completa, status `Candidato a FINAL` |
| Versionar GLB, brutos não escolhidos ou masters gigantes | Respeite o `.gitignore`; o manifesto diz como baixar de novo |
