// Prueba de carga corta que verifica el SLO del servicio de reservas contra el entorno desplegado.
// El SLO está escrito como umbrales (thresholds): si alguno no se cumple, k6 termina con código 99
// y el paso del pipeline falla.
//
// Uso (desde la raíz del repositorio, con el sistema arriba):
//   scripts/slo.sh
//   SLO_DURACION=5m SLO_TASA=50 scripts/slo.sh      (prueba larga, a mano)
import http from 'k6/http';
import encoding from 'k6/encoding';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8090';
const GESTOR = 'Basic ' + encoding.b64encode(`${__ENV.GESTOR_USUARIO}:${__ENV.GESTOR_CLAVE}`);
const CONSULTA = 'Basic ' + encoding.b64encode(`${__ENV.CONSULTA_USUARIO}:${__ENV.CONSULTA_CLAVE}`);

export const options = {
  scenarios: {
    slo: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.TASA || 20), // iteraciones por segundo
      timeUnit: '1s',
      duration: __ENV.DURACION || '30s',
      preAllocatedVUs: 10,
      maxVUs: 50,
    },
  },
  // ---------- SLO ----------
  thresholds: {
    http_req_duration: ['p(95)<500'], // el 95 % de las peticiones responde en menos de 500 ms
    http_req_failed: ['rate<0.01'], // menos del 1 % de las peticiones falla
    checks: ['rate>0.99'],
  },
};

// Un 409 (sala ocupada) es una respuesta correcta del negocio, no una falla del servicio.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 299 }, 409));

function fechaFutura() {
  const dias = 30 + Math.floor(Math.random() * 300);
  return new Date(Date.now() + dias * 86400000).toISOString().slice(0, 10);
}

function salaAleatoria() {
  const letra = String.fromCharCode(65 + Math.floor(Math.random() * 26));
  return `${letra}-${100 + Math.floor(Math.random() * 900)}`;
}

export default function () {
  const fecha = fechaFutura();

  // 4 de cada 5 iteraciones solo consultan (perfil de uso real: se consulta más de lo que se reserva).
  const lista = http.get(`${BASE_URL}/api/reservas?fecha=${fecha}`, {
    headers: { Authorization: CONSULTA },
    tags: { name: 'GET /api/reservas' },
  });
  check(lista, { 'consulta responde 200': (r) => r.status === 200 });

  if (Math.random() < 0.2) {
    const hora = 7 + Math.floor(Math.random() * 13);
    const inicio = `${String(hora).padStart(2, '0')}:00`;
    const fin = `${String(hora + 1).padStart(2, '0')}:00`;
    const cuerpo = JSON.stringify({
      sala: salaAleatoria(),
      responsable: 'prueba.carga@unisabana.edu.co',
      fecha,
      inicio,
      fin,
    });
    const creada = http.post(`${BASE_URL}/api/reservas`, cuerpo, {
      headers: { Authorization: GESTOR, 'Content-Type': 'application/json' },
      tags: { name: 'POST /api/reservas' },
    });
    check(creada, { 'crear responde 201 o 409': (r) => r.status === 201 || r.status === 409 });

    // Se cancela lo que se creó para no dejar ocupadas las salas con datos de prueba.
    if (creada.status === 201) {
      const cancelada = http.post(`${BASE_URL}/api/reservas/${creada.json('id')}/cancelacion`, null, {
        headers: { Authorization: GESTOR },
        tags: { name: 'POST /api/reservas/{id}/cancelacion' },
      });
      check(cancelada, { 'cancelar responde 200': (r) => r.status === 200 });
    }
  }
}
