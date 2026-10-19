#!/usr/bin/env bash
# Verifica, antes de desplegar, que la imagen fue firmada por el pipeline de CD de ESTE repositorio
# (firma sin llaves de Sigstore: el certificado identifica el workflow que la firmó).
# Si la firma no existe o la hizo otro workflow, otro repositorio o una persona, el script falla y no se despliega.
#
# Uso:
#   scripts/verificar-firma.sh <imagen:tag>
# Ejemplo:
#   scripts/verificar-firma.sh ghcr.io/<usuario>/<repositorio>:sha-1a2b3c4
#
# Imprime en la última línea la referencia fijada por digest (imagen:tag@sha256:...), que es la que se despliega:
# así se despliega exactamente la imagen cuya firma se verificó, aunque alguien mueva la etiqueta después.
# Los mensajes de avance van a la salida de errores para no mezclarse con esa línea.
#
# Variables:
#   GITHUB_REPOSITORY  usuario/repositorio dueño del workflow firmante (en GitHub Actions ya existe).
#   COSIGN_IMAGEN      imagen de cosign que se usa si cosign no está instalado (versión fija).
set -euo pipefail
IMAGEN="${1:?Uso: scripts/verificar-firma.sh <imagen:tag>}"
REPOSITORIO="${GITHUB_REPOSITORY:?Defina GITHUB_REPOSITORY=<usuario>/<repositorio>}"
COSIGN_IMAGEN="${COSIGN_IMAGEN:-ghcr.io/sigstore/cosign/cosign:v3.1.3}"

# Identidad esperada: el workflow cd.yml de este repositorio, ejecutado desde main o desde un tag de versión.
REPOSITORIO_RE="$(printf '%s' "$REPOSITORIO" | sed 's/[.]/[.]/g')"
IDENTIDAD="^https://github\.com/${REPOSITORIO_RE}/\.github/workflows/cd\.yml@refs/(heads/main|tags/v[0-9]+\.[0-9]+\.[0-9]+)$"
EMISOR="https://token.actions.githubusercontent.com"

cosign() {
  # type -P busca solo programas instalados (command -v encontraría esta misma función).
  if type -P cosign > /dev/null 2>&1; then
    command cosign "$@"
  else
    # Sin cosign instalado se usa su imagen oficial.
    local configuracion="${DOCKER_CONFIG:-$HOME/.docker}" montaje=()
    if pwd -W > /dev/null 2>&1; then export MSYS_NO_PATHCONV=1; fi
    # Las credenciales de "docker login" se montan en solo lectura para leer paquetes privados, pero solo si
    # están guardadas en config.json (como en el runner Linux). Docker Desktop las guarda en el gestor de
    # credenciales del sistema (credsStore), que no existe dentro del contenedor: allí no se montan y
    # cosign lee las imágenes públicas sin credenciales.
    if [[ -f "$configuracion/config.json" ]] && ! grep -qE '"(credsStore|credHelpers)"' "$configuracion/config.json"; then
      if pwd -W > /dev/null 2>&1; then configuracion="$(cd "$configuracion" && pwd -W)"; fi
      montaje=(-e DOCKER_CONFIG=/docker -v "$configuracion:/docker:ro")
    fi
    docker run --rm "${montaje[@]}" "$COSIGN_IMAGEN" "$@"
  fi
}

# 1. Se descarga la imagen y se obtiene su digest: el identificador del contenido, que no cambia aunque
#    alguien mueva la etiqueta. La firma se verifica sobre ese digest y es ese digest el que se despliega.
REPO="${IMAGEN%@*}"; REPO="${REPO%:*}"
echo "==> Descargando $IMAGEN para obtener su digest" >&2
docker pull --quiet "$IMAGEN" > /dev/null
REF_DIGEST="$(docker image inspect --format '{{range .RepoDigests}}{{println .}}{{end}}' "$IMAGEN" | grep -F "$REPO@" | head -1)"
if [[ -z "$REF_DIGEST" ]]; then
  echo "No se pudo obtener el digest de $IMAGEN en el registro." >&2
  exit 1
fi

# 2. Se verifica la firma de ese digest con la identidad esperada.
echo "==> Verificando la firma de $REF_DIGEST" >&2
echo "    identidad esperada: $IDENTIDAD" >&2
if ! cosign verify --certificate-identity-regexp "$IDENTIDAD" --certificate-oidc-issuer "$EMISOR" "$REF_DIGEST" > /dev/null; then
  echo "FIRMA NO VÁLIDA: $IMAGEN no fue firmada por el workflow cd.yml de $REPOSITORIO. No se despliega." >&2
  exit 1
fi

echo "==> Firma válida: $REF_DIGEST" >&2
# Se imprime etiqueta + digest (por ejemplo ghcr.io/u/r:sha-1a2b3c4@sha256:...): Docker usa el digest
# y la etiqueta queda solo para que las personas reconozcan la versión en los registros.
echo "${IMAGEN%@*}@${REF_DIGEST#*@}"
