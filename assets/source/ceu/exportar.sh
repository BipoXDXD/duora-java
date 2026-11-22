#!/usr/bin/env bash
# Exporta o céu pintado (masters 4K do Tripo) para a página em duas larguras, 16:9, WebP
# qualidade 88 (o mesmo padrão das imagens renderizadas pelo cena.py): 1280 para telas 1x e
# 2560 para telas 2x, escolhidas pelo image-set() do CSS. Uso: assets/source/ceu/exportar.sh
set -euo pipefail

AQUI="$(cd "$(dirname "$0")" && pwd)"
SAIDA="$AQUI/../../../src/main/resources/static/assets/ceu"
QUALIDADE=88

mkdir -p "$SAIDA"
for momento in crepusculo noite; do
  for largura in 1280 2560; do
    altura=$((largura * 9 / 16))
    cwebp -quiet -q "$QUALIDADE" -resize "$largura" "$altura" \
      "$AQUI/ceu-$momento-4k.png" -o "$SAIDA/ceu-$momento-$largura.webp"
  done
done
echo "ok: $SAIDA"
