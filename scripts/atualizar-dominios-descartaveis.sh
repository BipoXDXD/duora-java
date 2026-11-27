#!/usr/bin/env bash
# Baixa de novo a lista de domínios de e-mail descartável (disposable-email-domains, CC0) para
# src/main/resources/conta/dominios-descartaveis.txt, com cabeçalho de origem, licença e data.
# Uso, da raiz do repositório ou de qualquer pasta: scripts/atualizar-dominios-descartaveis.sh
set -euo pipefail

origem="https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf"
raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
destino="$raiz/src/main/resources/conta/dominios-descartaveis.txt"
temporario="$(mktemp)"
trap 'rm -f "$temporario"' EXIT

curl -fsSL "$origem" -o "$temporario"

if ! grep -qvE '^[[:space:]]*(#|$)' "$temporario"; then
  echo "A lista baixada veio vazia: $origem" >&2
  exit 1
fi

{
  echo "# Domínios de e-mail descartável."
  echo "# Origem: $origem"
  echo "# Licença: CC0 1.0 (domínio público)."
  echo "# Baixado em: $(date +%Y-%m-%d). Atualize com scripts/atualizar-dominios-descartaveis.sh."
  cat "$temporario"
} > "$destino"

echo "Lista atualizada: $(grep -cvE '^[[:space:]]*(#|$)' "$destino") domínios."
