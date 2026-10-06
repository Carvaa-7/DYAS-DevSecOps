#!/usr/bin/env bash
# Verifica el SLO con una prueba de carga corta (k6) contra el sistema desplegado con Docker Compose.
# Los umbrales están en perf/slo.js: si no se cumplen, k6 termina con un código distinto de 0 y este script falla.
#
# Uso:
#   scripts/slo.sh
#   SLO_DURACION=5m SLO_TASA=50 scripts/slo.sh     (prueba más larga)
# El resumen de k6 queda también en reportes/slo.txt.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p reportes

# k6 corre como un servicio de Compose (perfil "pruebas") dentro de la misma red que la aplicación.
docker compose run --rm k6 2>&1 | tee reportes/slo.txt
