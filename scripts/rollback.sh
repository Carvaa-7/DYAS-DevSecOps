#!/usr/bin/env bash
# Reversión: vuelve a desplegar una versión anterior de la imagen y ejecuta la prueba de humo.
#
# Uso:
#   scripts/rollback.sh <imagen:tag>   vuelve a la imagen indicada (recomendado)
#   scripts/rollback.sh                vuelve a la imagen que estaba antes del último despliegue
# Ejemplos:
#   scripts/rollback.sh ghcr.io/<usuario>/<repositorio>:sha-1a2b3c4
#   scripts/rollback.sh reservas:v1
set -euo pipefail

ESTADO_DIR="${DEPLOY_STATE_DIR:-$HOME/.reservas-despliegue}"
cd "$(dirname "$0")/.."

IMAGEN="${1:-$(cat "$ESTADO_DIR/anterior" 2>/dev/null || true)}"
if [[ -z "$IMAGEN" ]]; then
  echo "No hay una versión anterior registrada en $ESTADO_DIR. Indique la imagen: scripts/rollback.sh <imagen:tag>" >&2
  exit 1
fi

echo "==> Reversión a $IMAGEN"
scripts/deploy.sh "$IMAGEN"
scripts/smoke-test.sh
