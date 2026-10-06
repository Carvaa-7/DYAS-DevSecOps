#!/usr/bin/env bash
# Quality gate de escaneo de la imagen Docker con Trivy (sistema operativo y librerías dentro del contenedor).
# Umbral: ninguna vulnerabilidad CRITICAL que tenga corrección disponible. Si hay, el script falla.
#
# Uso:
#   scripts/escaneo-imagen.sh <imagen:tag>
# Ejemplo:
#   scripts/escaneo-imagen.sh reservas:local
# Deja el reporte completo (todas las severidades) en reportes/trivy-imagen.json.
set -euo pipefail
IMAGEN="${1:?Uso: scripts/escaneo-imagen.sh <imagen:tag>}"
cd "$(dirname "$0")/.."

TRIVY_IMAGEN="${TRIVY_IMAGEN:-aquasec/trivy:0.75.0}"
RAIZ="$(pwd)"
# En Git Bash (Windows), Docker necesita rutas con el formato D:/... y sin conversión automática.
if pwd -W > /dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  RAIZ="$(pwd -W)"
fi
mkdir -p reportes

# Trivy lee la imagen local a través del socket de Docker.
trivy() {
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$RAIZ:/src" -v trivy-cache:/root/.cache \
    "$TRIVY_IMAGEN" image --scanners vuln --ignorefile /src/.trivyignore "$@" "$IMAGEN"
}

echo "==> Reporte completo: reportes/trivy-imagen.json"
trivy --quiet --format json --output /src/reportes/trivy-imagen.json

echo "==> Quality gate: CRITICAL con corrección disponible"
trivy --severity CRITICAL --ignore-unfixed --exit-code 1
