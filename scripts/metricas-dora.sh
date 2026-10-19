#!/usr/bin/env bash
# Calcula las cuatro métricas DORA del servicio a partir del historial de GitHub Actions:
#   1. Frecuencia de despliegue        despliegues exitosos por semana.
#   2. Tiempo de espera de los cambios  desde el commit hasta que su versión queda desplegada y verificada (mediana).
#   3. Tasa de fallos de los cambios    porcentaje de despliegues que fallaron (humo, SLO o despliegue).
#   4. Tiempo de restauración           desde un despliegue fallido hasta el siguiente despliegue exitoso (mediana).
#
# Un "despliegue" es una ejecución del job "Desplegar y verificar" de cd.yml (push a main o tag vX.Y.Z).
# Las ejecuciones en las que ese job quedó omitido (DESPLIEGUE distinto de "activo") no cuentan.
#
# Uso:
#   scripts/metricas-dora.sh [usuario/repositorio]
# Variables:
#   GITHUB_REPOSITORY  repositorio (en GitHub Actions ya existe; en local se puede pasar como argumento).
#   GITHUB_TOKEN       token con permiso de lectura de Actions. Sin token funciona en repositorios públicos,
#                      con el límite de 60 consultas por hora de la API de GitHub.
#   EJECUCIONES        cuántas ejecuciones recientes de cd.yml se revisan (por defecto 50, máximo 100).
#   DORA_JOB           nombre del job que se cuenta como despliegue (por defecto "Desplegar y verificar").
# Requiere curl y jq (ambos vienen instalados en los runners de GitHub).
# Deja el resultado en reportes/dora.md.
set -euo pipefail
REPOSITORIO="${1:-${GITHUB_REPOSITORY:?Uso: scripts/metricas-dora.sh <usuario/repositorio>}}"
API="${GITHUB_API_URL:-https://api.github.com}"
EJECUCIONES="${EJECUCIONES:-50}"
DORA_JOB="${DORA_JOB:-Desplegar y verificar}"
cd "$(dirname "$0")/.."
mkdir -p reportes

api() {
  local cabeceras=(-H "Accept: application/vnd.github+json" -H "X-GitHub-Api-Version: 2022-11-28")
  if [[ -n "${GITHUB_TOKEN:-}" ]]; then cabeceras+=(-H "Authorization: Bearer $GITHUB_TOKEN"); fi
  curl -fsSL "${cabeceras[@]}" "$API/repos/$REPOSITORIO/$1"
}

echo "==> Revisando las últimas $EJECUCIONES ejecuciones de cd.yml en $REPOSITORIO" >&2
EJEC="$(api "actions/workflows/cd.yml/runs?event=push&status=completed&per_page=$EJECUCIONES")"

# Por cada ejecución se busca el job de despliegue y se guarda: hora del commit, hora en que terminó el job y resultado.
DESPLIEGUES="$(
  echo "$EJEC" | jq -r '.workflow_runs[] | [.id, .head_commit.timestamp, .head_sha[0:7]] | @tsv' |
  while IFS=$'\t' read -r ID COMMIT SHA; do
    api "actions/runs/$ID/jobs?per_page=50" |
      jq -c --arg job "$DORA_JOB" --arg commit "$COMMIT" --arg sha "$SHA" '
        .jobs[] | select(.name == $job and (.conclusion == "success" or .conclusion == "failure"))
        | {sha: $sha, commit: $commit, fin: .completed_at, exito: (.conclusion == "success")}'
  done
)"

# jq hace los cálculos. fromdateiso8601 no acepta zonas horarias como -05:00, por eso la función seg.
echo "$DESPLIEGUES" | jq -rs --arg repo "$REPOSITORIO" --arg job "$DORA_JOB" '
  def seg: if test("Z$") then sub("[.][0-9]+Z$"; "Z") | fromdateiso8601
           else (.[0:19] + "Z" | fromdateiso8601)
                - ((if .[19:20] == "-" then -1 else 1 end) * ((.[20:22] | tonumber) * 3600 + (.[23:25] | tonumber) * 60))
           end;
  def mediana: sort | if length == 0 then null
               elif length % 2 == 1 then .[length / 2 | floor]
               else (.[length / 2 - 1] + .[length / 2]) / 2 end;
  def duracion: if . == null then "sin datos"
                elif . < 3600 then "\(. / 60 | floor) min"
                elif . < 172800 then "\(. / 3600 * 10 | floor / 10) h"
                else "\(. / 86400 * 10 | floor / 10) días" end;

  map(. + {t: (.fin | seg), tc: (.commit | seg)}) | sort_by(.t) as $d
  | ($d | map(select(.exito))) as $ok
  | ($d | map(select(.exito | not))) as $mal
  | (if ($d | length) > 1 then (($d[-1].t - $d[0].t) / 604800) else 1 end | if . < 1 then 1 else . end) as $semanas
  | [ $mal[] as $f | ($ok | map(select(.t > $f.t)) | first) as $sig | select($sig != null) | $sig.t - $f.t ] as $restauraciones
  | "## Métricas DORA de \($repo)\n",
    "Despliegues revisados: \($d | length) (exitosos: \($ok | length), fallidos: \($mal | length)). Job contado: \"\($job)\".\n",
    "| Métrica | Valor |",
    "|---|---|",
    "| Frecuencia de despliegue | \(($ok | length) / $semanas * 10 | floor / 10) despliegues exitosos por semana |",
    "| Tiempo de espera de los cambios (mediana) | \($ok | map(.t - .tc) | mediana | duracion) |",
    "| Tasa de fallos de los cambios | \(if ($d | length) == 0 then "sin datos" else "\(($mal | length) * 1000 / ($d | length) | floor / 10) %" end) |",
    "| Tiempo de restauración (mediana) | \($restauraciones | mediana | duracion) |",
    "",
    (if ($d | length) == 0 then "No hay despliegues en las ejecuciones revisadas: revise que la variable DESPLIEGUE valga \"activo\"." else empty end)
' | tee reportes/dora.md
