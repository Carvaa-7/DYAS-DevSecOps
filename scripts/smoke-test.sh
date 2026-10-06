#!/usr/bin/env bash
# Prueba de humo: comprueba que el sistema desplegado responde. Si algo falla, termina con código 1.
#
# Uso:
#   scripts/smoke-test.sh [version-esperada]
# Variables:
#   BASE_URL                       por defecto http://localhost:8090
#   GESTOR_USUARIO, GESTOR_CLAVE   del entorno o del archivo .env
set -euo pipefail
cd "$(dirname "$0")/.."

if [[ -z "${GESTOR_USUARIO:-}" && -f .env ]]; then
  set -a; source .env; set +a
fi
: "${GESTOR_USUARIO:?Defina GESTOR_USUARIO}" "${GESTOR_CLAVE:?Defina GESTOR_CLAVE}"

BASE_URL="${BASE_URL:-http://localhost:8090}"
VERSION_ESPERADA="${1:-}"
CREDENCIALES="$GESTOR_USUARIO:$GESTOR_CLAVE"

falla() { echo "PRUEBA DE HUMO FALLIDA: $*" >&2; exit 1; }
ok() { echo "  ok  $*"; }

echo "==> Prueba de humo contra $BASE_URL"

# 1. El servicio está arriba (se reintenta mientras termina de arrancar).
salud="$(curl -fsS --retry 10 --retry-delay 3 --retry-all-errors "$BASE_URL/actuator/health")" \
  || falla "no responde $BASE_URL/actuator/health"
[[ "$salud" == *'"status":"UP"'* ]] || falla "health no está UP: $salud"
ok "health UP"

# 2. Responde la versión que se acaba de desplegar.
info="$(curl -fsS "$BASE_URL/actuator/info")" || falla "no responde /actuator/info"
if [[ -n "$VERSION_ESPERADA" ]]; then
  [[ "$info" == *"\"version\":\"$VERSION_ESPERADA\""* ]] || falla "se esperaba la versión $VERSION_ESPERADA y responde $info"
  ok "versión $VERSION_ESPERADA"
else
  ok "versión desplegada: $info"
fi

# 3. La API exige autenticación.
codigo="$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/api/reservas?fecha=2030-01-01")"
[[ "$codigo" == "401" ]] || falla "sin credenciales se esperaba 401 y respondió $codigo"
ok "sin credenciales responde 401"

# 4. Flujo principal: crear, consultar y cancelar una reserva (la base de datos responde).
fecha="$(date -d '+60 days' +%F 2>/dev/null || date -v+60d +%F)"
sala="Z-$((RANDOM % 900 + 100))"
hora="$(printf '%02d' $((RANDOM % 13 + 7)))"
cuerpo="{\"sala\":\"$sala\",\"responsable\":\"prueba.humo@unisabana.edu.co\",\"fecha\":\"$fecha\",\"inicio\":\"$hora:00\",\"fin\":\"$hora:30\"}"

respuesta="$(curl -fsS -u "$CREDENCIALES" -H 'Content-Type: application/json' -d "$cuerpo" "$BASE_URL/api/reservas")" \
  || falla "no se pudo crear la reserva de prueba"
id="$(sed -E 's/.*"id":"([^"]+)".*/\1/' <<< "$respuesta")"
ok "reserva creada ($id)"

curl -fsS -u "$CREDENCIALES" "$BASE_URL/api/reservas/$id" > /dev/null || falla "no se pudo consultar la reserva $id"
ok "reserva consultada"

curl -fsS -u "$CREDENCIALES" -X POST "$BASE_URL/api/reservas/$id/cancelacion" > /dev/null \
  || falla "no se pudo cancelar la reserva $id"
ok "reserva cancelada"

echo "==> Prueba de humo superada"
