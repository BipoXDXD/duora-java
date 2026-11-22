#!/usr/bin/env bash
# Monta os exports de áudio a partir dos brutos do ElevenLabs (assets/source/audio/brutos/).
# Uso: assets/source/audio/montar.sh   (precisa de ffmpeg)
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
SAIDA="$AQUI/../../../src/main/resources/static/assets/cenarios/terraco/audio"
mkdir -p "$SAIDA"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
SR=48000  # tudo a 48 kHz (taxa nativa da trilha)
FF=(ffmpeg -hide_banner -loglevel error -y)

# Trilha: 64 s a partir de 3 s; o fim (4 s) entra em crossfade com o começo -> loop de 60 s.
"${FF[@]}" -i "$AQUI/brutos/trilha-1.mp3" -af "atrim=start=3:end=67,asetpts=N/SR/TB,aresample=${SR}" -ac 2 "$TMP/seg.wav"
"${FF[@]}" -i "$TMP/seg.wav" -af "atrim=end=4,asetpts=N/SR/TB" "$TMP/cabeca.wav"
"${FF[@]}" -i "$TMP/seg.wav" -af "atrim=start=4:end=60,asetpts=N/SR/TB" "$TMP/corpo.wav"
"${FF[@]}" -i "$TMP/seg.wav" -af "atrim=start=60,asetpts=N/SR/TB" "$TMP/cauda.wav"
"${FF[@]}" -i "$TMP/cauda.wav" -i "$TMP/cabeca.wav" -filter_complex "[0][1]acrossfade=d=4:c1=qsin:c2=qsin" "$TMP/junta.wav"
"${FF[@]}" -i "$TMP/junta.wav" -i "$TMP/corpo.wav" -filter_complex "[0][1]concat=n=2:v=0:a=1" "$TMP/trilha.wav"

# Ambiência do terraço: gerada em loop (30 s), repetida 2x para casar com a trilha.
"${FF[@]}" -i "$AQUI/brutos/amb-4.mp3" -af "aresample=${SR},atrim=end_sample=$((30 * SR)),asetpts=N/SR/TB" -ac 2 "$TMP/amb30.wav"
"${FF[@]}" -stream_loop 1 -i "$TMP/amb30.wav" -c copy "$TMP/amb60.wav"

# Mistura: trilha ~-20 LUFS, ambiência ~12 dB abaixo, limitador de segurança.
"${FF[@]}" -i "$TMP/trilha.wav" -i "$TMP/amb60.wav" \
  -filter_complex "[0]volume=-5.3dB[m];[1]volume=8.9dB[a];[m][a]amix=inputs=2:normalize=0,alimiter=limit=0.89" \
  -c:a libmp3lame -b:a 192k "$SAIDA/ambiente.mp3"

# Efeitos: loudness -20 LUFS e fade curto no fim.
"${FF[@]}" -i "$AQUI/brutos/met-2.mp3" -af "loudnorm=I=-20:TP=-2:LRA=11,afade=t=out:st=1.25:d=0.35,aresample=${SR}" -ac 2 -c:a libmp3lame -b:a 192k "$SAIDA/meteoro.mp3"
"${FF[@]}" -i "$AQUI/brutos/rev-2.mp3" -af "loudnorm=I=-20:TP=-2:LRA=11,afade=t=out:st=3.2:d=0.8,aresample=${SR}" -ac 2 -c:a libmp3lame -b:a 192k "$SAIDA/revelacao.mp3"

echo "ok: $SAIDA"
