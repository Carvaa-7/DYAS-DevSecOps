#!/usr/bin/env bash
# Quality gate de análisis de dependencias (SCA) con Trivy.
# Umbral: ninguna vulnerabilidad HIGH o CRITICAL que tenga corrección disponible. Si hay, el script falla.
#
# Uso:
#   scripts/sca.sh
# Deja el reporte completo (todas las severidades) en reportes/trivy-dependencias.json.
set -euo pipefail
cd "$(dirname "$0")/.."

TRIVY_IMAGEN="${TRIVY_IMAGEN:-aquasec/trivy:0.75.0}"
RAIZ="$(pwd)"
M2="$HOME/.m2"
# En Git Bash (Windows), Docker necesita rutas con el formato D:/... y sin conversión automática.
if pwd -W > /dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  RAIZ="$(pwd -W)"
  M2="$(cd ~ && pwd -W)/.m2"
fi
mkdir -p reportes

# Trivy lee el pom.xml y resuelve las dependencias transitivas. Se le pasa la caché local de Maven
# para que no tenga que descargar los pom de Maven Central (que limita las peticiones repetidas).
echo "==> Descargando las dependencias a la caché local de Maven"
mvn -B -q -ntp dependency:go-offline

trivy() {
  docker run --rm -v "$RAIZ:/src" -v "$M2:/root/.m2:ro" -v trivy-cache:/root/.cache \
    "$TRIVY_IMAGEN" fs --scanners vuln --skip-dirs target --ignorefile /src/.trivyignore "$@" /src
}

echo "==> Reporte completo: reportes/trivy-dependencias.json"
trivy --quiet --format json --output /src/reportes/trivy-dependencias.json

echo "==> Quality gate: HIGH o CRITICAL con corrección disponible"
trivy --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1
