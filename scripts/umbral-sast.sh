#!/usr/bin/env bash
# Quality gate de análisis estático (SAST): revisa el reporte SARIF de CodeQL y falla si hay hallazgos
# con severidad de seguridad alta o crítica (security-severity >= 7.0) que no estén tratados.
#
# Uso:
#   scripts/umbral-sast.sh <archivo.sarif> [archivo.sarif ...]
# Requiere jq (viene instalado en los runners ubuntu-latest de GitHub Actions).
#
# Un hallazgo se considera tratado si su regla y su archivo aparecen en .github/codeql/excepciones-sast.txt,
# y cada línea de ese archivo debe tener su registro en docs/seguridad.md (falso positivo o riesgo aceptado).
set -euo pipefail
cd "$(dirname "$0")/.."

UMBRAL="7.0"
EXCEPCIONES=".github/codeql/excepciones-sast.txt"
[[ $# -gt 0 ]] || { echo "Uso: scripts/umbral-sast.sh <archivo.sarif> [...]" >&2; exit 2; }

# Excepciones en formato JSON: [{"regla": "...", "archivo": "..."}]
excepciones='[]'
if [[ -f "$EXCEPCIONES" ]]; then
  excepciones="$({ grep -vE '^[[:space:]]*(#|$)' "$EXCEPCIONES" || true; } \
    | awk '{print $1" "$2}' \
    | jq -R -s 'split("\n") | map(select(length > 0) | split(" ") | {regla: .[0], archivo: .[1]})')"
fi

hallazgos="$(jq -s --argjson umbral "$UMBRAL" --argjson excepciones "$excepciones" '
  [ .[].runs[] as $run
    # Severidad de cada regla, según los metadatos de la consulta de CodeQL
    | ([$run.tool.driver.rules[]?, $run.tool.extensions[]?.rules[]?]
        | map({key: .id, value: ((.properties["security-severity"] // "0") | tonumber)})
        | from_entries) as $severidad
    | $run.results[]?
    | {
        regla: .ruleId,
        severidad: ($severidad[.ruleId] // 0),
        archivo: .locations[0].physicalLocation.artifactLocation.uri,
        linea: .locations[0].physicalLocation.region.startLine,
        mensaje: .message.text
      }
    | select(.severidad >= $umbral)
    | . as $h
    | .tratado = any($excepciones[]; .regla == $h.regla and .archivo == $h.archivo)
  ]' "$@")"

echo "==> Hallazgos de SAST con severidad >= $UMBRAL"
jq -r '.[] | "  [\(if .tratado then "tratado" else "PENDIENTE" end)] \(.severidad) \(.regla) \(.archivo):\(.linea) \(.mensaje)"' <<< "$hallazgos"

pendientes="$(jq '[.[] | select(.tratado | not)] | length' <<< "$hallazgos")"
total="$(jq 'length' <<< "$hallazgos")"
echo "==> $total hallazgo(s) altos o críticos, $pendientes sin tratar"

if [[ "$pendientes" -gt 0 ]]; then
  echo "QUALITY GATE DE SAST FALLIDO: corrija los hallazgos o regístrelos en docs/seguridad.md" >&2
  exit 1
fi
