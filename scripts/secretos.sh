#!/usr/bin/env bash
# Quality gate de detección de secretos con Gitleaks, sobre TODO el historial de Git.
# Umbral: cero secretos. Si encuentra uno, el script falla.
#
# Uso:
#   scripts/secretos.sh
# Deja el reporte en reportes/gitleaks.json (los valores encontrados aparecen ocultos con --redact).
set -euo pipefail
cd "$(dirname "$0")/.."

GITLEAKS_IMAGEN="${GITLEAKS_IMAGEN:-zricethezav/gitleaks:v8.30.1}"
RAIZ="$(pwd)"
# En Git Bash (Windows), Docker necesita rutas con el formato D:/... y sin conversión automática.
if pwd -W > /dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  RAIZ="$(pwd -W)"
fi
mkdir -p reportes

# "git" revisa cada commit del historial, no solo los archivos actuales.
# -w /repo hace que Gitleaks lea el archivo .gitleaksignore de la raíz del repositorio, si existe.
docker run --rm -v "$RAIZ:/repo" -w /repo "$GITLEAKS_IMAGEN" git /repo \
  --redact --verbose --exit-code 1 \
  --report-format json --report-path /repo/reportes/gitleaks.json
