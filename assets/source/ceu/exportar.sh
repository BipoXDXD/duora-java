#!/usr/bin/env bash
# Exporta o céu pintado (masters 4K do Tripo) para a página: 2560×1440, WebP qualidade 88
# (o mesmo padrão das imagens renderizadas pelo cena.py). Uso: assets/source/ceu/exportar.sh
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
SAIDA="$AQUI/../../../src/main/resources/static/assets/ceu"
QUALIDADE=88

mkdir -p "$SAIDA"
for momento in crepusculo noite; do
  cwebp -quiet -q "$QUALIDADE" -resize 2560 1440 "$AQUI/ceu-$momento-4k.png" -o "$SAIDA/ceu-$momento.webp"
done
echo "ok: $SAIDA"
