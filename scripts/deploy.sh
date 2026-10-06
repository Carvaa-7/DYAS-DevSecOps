#!/usr/bin/env bash
# Despliega una versión de la imagen con Docker Compose y espera a que quede saludable.
#
# Uso:
#   scripts/deploy.sh <imagen:tag>
# Ejemplos:
#   scripts/deploy.sh ghcr.io/<usuario>/<repositorio>:sha-1a2b3c4   (imagen del registro: se descarga)
#   scripts/deploy.sh reservas:v2                                    (imagen local: no se descarga)
#
# Las variables del sistema (POSTGRES_*, GESTOR_*, CONSULTA_*) se toman del entorno o del archivo .env.
# El script guarda en $DEPLOY_STATE_DIR (por defecto ~/.reservas-despliegue) qué imagen estaba antes,
# para que scripts/rollback.sh pueda volver a ella.
set -euo pipefail

IMAGEN="${1:?Uso: scripts/deploy.sh <imagen:tag>}"
ESTADO_DIR="${DEPLOY_STATE_DIR:-$HOME/.reservas-despliegue}"
cd "$(dirname "$0")/.."
mkdir -p "$ESTADO_DIR"

echo "==> Desplegando $IMAGEN"
export APP_IMAGE="$IMAGEN"

# Si la imagen viene de un registro (tiene "/"), se descarga. Una imagen local se usa tal cual.
if [[ "$IMAGEN" == */* ]]; then
  docker compose pull app
fi

# Se registra la versión que estaba desplegada antes de cambiarla.
ACTUAL="$(cat "$ESTADO_DIR/actual" 2>/dev/null || true)"
if [[ -n "$ACTUAL" && "$ACTUAL" != "$IMAGEN" ]]; then
  echo "$ACTUAL" > "$ESTADO_DIR/anterior"
fi
echo "$IMAGEN" > "$ESTADO_DIR/actual"

# --wait espera a que los contenedores estén "healthy"; si no lo logran, el comando (y el script) falla.
docker compose up -d --no-build --wait --wait-timeout 180

echo "==> $IMAGEN desplegada y saludable"
docker compose ps
