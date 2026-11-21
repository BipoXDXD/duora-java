# Manifesto de assets

Masters ficam em `assets/source/`; exports usados pela página, em `src/main/resources/static/assets/`.
Padrão de exportação: imagens em WebP qualidade 88; áudio em MP3 192 kbps a 48 kHz.
Status: `PROVISÓRIO`, `Candidato a FINAL` (gerado, aguarda o aceite visual do dono) ou `FINAL`. Nenhum export provisório aparece na página.

## Cena

| Export | Origem | Ferramenta | Licença | Status |
|---|---|---|---|---|
| `cena/terraco-{crepusculo,noite}.webp` | `source/blender/cena.py` (geometria procedural) | Blender 5.3, Cycles, câmera ortográfica | Própria | Candidato a FINAL |
| `cena/{telescopio-esq,telescopio-dir,banco,manta,radio,lanterna,garrafa}-{crepusculo,noite}.webp` | `source/modelos/*.glb` (Hi3D) importados pelo `cena.py`, malha reduzida a 8% | Hi3D (imagem Nano-Banana 2 → 3D v3.0, PBR) + Blender 5.3 | Privada (conta do dono no Hi3D) | Candidato a FINAL (aguarda aceite) |
| `ceu/ceu-{crepusculo,noite}.webp` (2560×1440, WebP q88, `source/ceu/exportar.sh`) | `source/ceu/ceu-{crepusculo,noite}-4k.png` | Tripo `chat_image_2.5_sunburst`, quality high (noite: texto→imagem; crepúsculo: edição da noite). Lua descida ~480 px no master por edição local (PIL), igual nas duas | Privada (conta do dono no Tripo) | Candidato a FINAL |

### Gerações Hi3D (pela assinatura/OAuth)

| Asset | Tarefa de imagem (4 pts) | Tarefa 3D (45 pts) | Master local |
|---|---|---|---|
| Telescópio | `ebb36d800b0343f9b76f27bab0669d67` | `5f383de263694d8cb84f2eb5e2a3b234` | `source/referencias/telescopio-hi3d-ebb36d80.png`, `source/modelos/telescopio.glb` |
| Banco | `cc9a54a0b63e4d28849736cbb5e566a2` | `78c7c23d5e5f4c8da932e882609657c9` | `source/referencias/banco-hi3d-cc9a54a0.png`, `source/modelos/banco.glb` |
| Manta | `47ee75ce9615453785e6ada509929ba8` | `c7ff06a295fc41de9972c732934e1ae9` | `source/referencias/manta-hi3d-47ee75ce.png`, `source/modelos/manta.glb` |
| Rádio | `06dd8d7ecea6437b8374b98d9b7ac230` | `46fb35db6ff44ca099e189ad6710c734` | `source/referencias/radio-hi3d-06dd8d7e.png`, `source/modelos/radio.glb` |
| Lanterna | `89e9c8d1e74b414298e0e2f01368266b` | `59aab75697574146958193e9674e7e02` | `source/referencias/lanterna-hi3d-89e9c8d1.png`, `source/modelos/lanterna.glb` |
| Garrafa | `37627b37b75b43adbf71573742627d27` | `c78c45b976c1455b9447f4797a692435` | `source/referencias/garrafa-hi3d-37627b37.png`, `source/modelos/garrafa.glb` |

Total Hi3D: 6 × (4 + 45) = 294 pts. Total Tripo (céu): 2 × 40 = 80 créditos (tarefas `f6e4ca3f-10ac-4e86-8690-773002c81569` e `2be7bd5e-f7db-455d-b988-9805c4458647`; `source/ceu/tripo-*-task.json`).

Os GLBs têm ~48 MB (≈2 milhões de faces, texturas 8K) e ficam fora do Git (`.gitignore`). Para baixar de novo, use `get_task_outputs` no MCP do Hi3D com o ID da tarefa 3D e salve em `assets/source/modelos/<nome>.glb`. Sem o GLB, o `cena.py` volta ao prop procedural. Giros padrão: telescópio 90°, banco 180° (encosto para a câmera), demais 0°. A vela da lanterna vem apagada no modelo; o brilho noturno é um halo + poço de luz em CSS.
| `cena/cena.json` | Gerado pelo mesmo script | Blender | Própria | Gerado |
| `icone.svg` | Desenhado à mão (vetor) | — | Própria | Candidato a FINAL |

Regenerar (sem créditos):

```sh
/Applications/Blender.app/Contents/MacOS/Blender -b -P assets/source/blender/cena.py -- "$PWD"
# DUORA_SO=terraco|banco|... renderiza só uma parte; DUORA_AMOSTRAS=32 acelera testes.
```

## Áudio

Gerado no ElevenLabs (flow `1v6oSiEKugInq7KOaP0r`, "Duora — Antes da primeira estrela (áudio)"), 4 variações por som; escolhidas por medição (loudness, LRA) e espectrograma. Montagem e normalização locais: `source/audio/montar.sh` (ffmpeg); todos os exports em MP3 192 kbps a 48 kHz. Licença: conta do dono no ElevenLabs (plano Creator). Status: candidato a FINAL.

| Export | Bruto escolhido | Modelo | Geração | Tratamento |
|---|---|---|---|---|
| `audio/ambiente.mp3` (60 s, loop, 192 kbps) | `brutos/trilha-1.mp3` + `brutos/amb-4.mp3` | `eleven_music_v2_5` (75 s, instrumental) + `eleven_text_to_sound_v3` (30 s, loop) | `fKL1ZLtQgXCJrs8Wozxw` + `NsfI9LaOcHiABvoptabd` | trilha recortada com crossfade de 4 s no laço; ambiência ~12 dB abaixo |
| `audio/meteoro.mp3` (1,6 s, 192 kbps) | `brutos/met-2.mp3` | `eleven_text_to_sound_v3` | `aSb0KGD3NtHW5csUciBX` | −20 LUFS, fade de 0,35 s |
| `audio/revelacao.mp3` (4 s, 192 kbps) | `brutos/rev-2.mp3` | `eleven_text_to_sound_v3` | `8JgqRj9O0Str52ENxIdK` | −20 LUFS, fade de 0,8 s |

Custo: 16 gerações = ~4.972 créditos ElevenLabs (trilhas 4 × 1.125; ambiência 4 × 100; meteoro 4 × 5; revelação 4 × 13). Os brutos não escolhidos ficam fora do Git.

## Fontes

| Export | Origem | Licença |
|---|---|---|
| `fonts/atkinson-next-{400,600}.woff2` | `@fontsource/atkinson-hyperlegible-next@5.3.0` (subconjunto latin) | SIL OFL 1.1 (`fonts/OFL-atkinson-hyperlegible-next.txt`) |
| `fonts/fraunces-600.woff2` | `@fontsource/fraunces@5.3.0` (subconjunto latin) | SIL OFL 1.1 (`fonts/OFL-fraunces.txt`) |

## Brief usado para os props (histórico)

Substituem os props low-poly mantendo câmera, luz e escala do `cena.py`. A ferramenta principal é o Hi3D (assinatura/OAuth), com imagem→3D a partir de um quadro de estilo. Fallback: Rodin Business, informando o custo antes. O GLB é importado no `cena.py` no lugar do construtor procedural.

- **Estilo:** miniatura de diorama, materiais táteis (madeira, latão, tecido, esmalte), formas simples e legíveis em 60 px de altura, sem texto nem logotipos, sem rostos.
- **Escala (m):** telescópio com tripé ~1,9 de altura; banco 1,6 × 0,5 × 0,95; manta 1,35 × 1,0 dobrada no chão, com almofada; rádio de válvula sobre caixote, conjunto ~0,85 de altura; lanterna de ferro e vidro ~0,95, com vela; garrafa térmica com duas xícaras sobre mesinha redonda, ~1,0.
- **Cores-guia:** tubo azul esmalte `#2E4A66` com latão `#C9A15A`; madeira `#A0703F`; manta vinho `#7E3B45` com faixas âmbar `#D9A441`; rádio creme `#E8DCC2`; garrafa verde-água `#6FA79A`.
- **Rejeitar:** perspectiva ou iluminação embutida, halos, deformações, aparência de clipart, objetos que não se distinguem no celular.
