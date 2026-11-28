#!/usr/bin/env bash
# Confere a imagem de produção já construída: libvips >= 8.15 (o boot exige), processo fora do
# root e HEALTHCHECK declarado. Uso: scripts/ci/checar-imagem.sh duora-java:ci
set -euo pipefail

imagem="${1:?uso: $0 <imagem>}"

saida=$(docker run --rm --entrypoint vips "$imagem" --version)
versao=$(sed -n 's/^vips-\([0-9]*\.[0-9]*\).*/\1/p' <<<"$saida" | head -n 1)
maior=${versao%%.*}
menor=${versao#*.}
if [[ -z "$versao" ]] || ((maior < 8 || (maior == 8 && menor < 15))); then
  echo "libvips >= 8.15 exigido; a imagem traz: $saida" >&2
  exit 1
fi
echo "libvips ok: $saida"

uid=$(docker run --rm --entrypoint id "$imagem" -u)
if [[ "$uid" == "0" ]]; then
  echo "o processo da imagem roda como root" >&2
  exit 1
fi
echo "usuário ok: uid $uid"

teste=$(docker inspect --format '{{json .Config.Healthcheck.Test}}' "$imagem")
if [[ "$teste" == "null" || -z "$teste" ]]; then
  echo "a imagem não declara HEALTHCHECK" >&2
  exit 1
fi
echo "healthcheck ok: $teste"
