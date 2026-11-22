#!/usr/bin/env bash
# Chaves de cache do front, definidas num lugar só para os jobs da CI:
# - chave-node: versões do Node e do pnpm lidas do pom.xml (a fonte única delas);
# - chave-store: impressão do pnpm-lock.yaml, para o store do pnpm.
# Escreve em $GITHUB_OUTPUT. Uso local: GITHUB_OUTPUT=/dev/stdout scripts/ci/versoes-do-front.sh
set -euo pipefail

pom="${1:-pom.xml}"
lock="${2:-frontend/pnpm-lock.yaml}"

propriedade() {
  sed -n "s:.*<$1>\(.*\)</$1>.*:\1:p" "$pom" | head -n 1
}

node=$(propriedade node.version)
pnpm=$(propriedade pnpm.version)
if [[ -z "$node" || -z "$pnpm" ]]; then
  echo "node.version ou pnpm.version ausente em $pom" >&2
  exit 1
fi

if command -v sha256sum > /dev/null; then
  impressao=$(sha256sum "$lock" | cut -d ' ' -f 1)
else
  impressao=$(shasum -a 256 "$lock" | cut -d ' ' -f 1)
fi

{
  echo "chave-node=node-$node-pnpm-$pnpm"
  echo "chave-store=pnpm-store-$impressao"
} >> "$GITHUB_OUTPUT"
