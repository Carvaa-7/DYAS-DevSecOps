#!/usr/bin/env bash
# Genera el SBOM (lista de materiales de software) de una imagen Docker en formato CycloneDX con Trivy.
# El SBOM enumera cada componente que quedó dentro de la imagen: paquetes del sistema operativo y librerías Java
# con su versión. No es un quality gate: sirve para saber qué se desplegó y para revisar después si una
# vulnerabilidad nueva afecta a una versión ya publicada.
#
# Uso:
#   scripts/sbom.sh <imagen:tag>
# Ejemplo:
#   scripts/sbom.sh reservas:local
# Deja el SBOM en reportes/sbom-imagen.cdx.json.
set -euo pipefail
IMAGEN="${1:?Uso: scripts/sbom.sh <imagen:tag>}"
cd "$(dirname "$0")/.."

TRIVY_IMAGEN="${TRIVY_IMAGEN:-aquasec/trivy:0.76.0}"
RAIZ="$(pwd)"
# En Git Bash (Windows), Docker necesita rutas con el formato D:/... y sin conversión automática.
if pwd -W > /dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  RAIZ="$(pwd -W)"
fi
mkdir -p reportes

echo "==> Generando el SBOM de $IMAGEN (CycloneDX)"
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v "$RAIZ:/src" -v trivy-cache:/root/.cache \
  "$TRIVY_IMAGEN" image --quiet --format cyclonedx --output /src/reportes/sbom-imagen.cdx.json "$IMAGEN"

# Resumen: cuántos componentes hay y de qué tipo (librerías Java, paquetes del sistema operativo).
COMPONENTES="$(grep -o '"bom-ref"' reportes/sbom-imagen.cdx.json | wc -l | tr -d ' ')"
JARS="$(grep -o '"purl": *"pkg:maven/' reportes/sbom-imagen.cdx.json | wc -l | tr -d ' ')"
SO="$(grep -oE '"purl": *"pkg:(deb|apk|rpm)/' reportes/sbom-imagen.cdx.json | wc -l | tr -d ' ')"
echo "==> SBOM en reportes/sbom-imagen.cdx.json: $COMPONENTES componentes ($JARS librerías Java, $SO paquetes del sistema operativo)"
