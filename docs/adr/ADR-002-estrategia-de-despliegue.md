# ADR-002. Estrategia de despliegue continuo

**Estado:** aceptada

## Contexto

Cada versión que llega a `main` debe desplegarse sin pasos manuales en un entorno accesible, verificarse con una prueba de humo y con la prueba de carga del SLO, y poder revertirse a la versión anterior. El equipo no tiene presupuesto para servicios en la nube de pago y dispone de una máquina propia (o de laboratorio) con Docker.

## Decisión

1. **Registro de imágenes:** GitHub Container Registry (GHCR). Cada imagen se publica con dos etiquetas: la versión (`1.0.0`) y el SHA corto del commit (`sha-1a2b3c4`). Se despliega siempre la etiqueta `sha-...`, que no cambia de contenido. Nunca se usa solo `latest`.
2. **Entorno:** una máquina del equipo con Docker Engine y el plugin de Compose, con un **runner autohospedado** de GitHub Actions registrado con la etiqueta `dyas`.
3. **Mecanismo:** el job `desplegar` de `cd.yml` corre en ese runner y ejecuta `scripts/deploy.sh`, que hace `docker compose pull` y `docker compose up -d --wait` con la imagen nueva. `--wait` falla si el contenedor no llega a estado *healthy*.
4. **Verificación:** después del despliegue corren `scripts/smoke-test.sh` (salud, versión desplegada, autenticación y flujo crear, consultar y cancelar) y `scripts/slo.sh` (k6 con los umbrales del SLO). Si alguno falla, el pipeline falla.
5. **Reversión:** si la verificación falla, el mismo job ejecuta `scripts/rollback.sh`, que vuelve a desplegar la imagen anterior y repite la prueba de humo. También se puede revertir a mano desde la pestaña Actions (`workflow_dispatch` con el tag de la imagen).
6. **Secretos:** las claves de la base de datos y de los usuarios de la API viven en el environment `produccion` de GitHub (Secrets). Los valores no sensibles van en Variables.

## Alternativas consideradas

| Alternativa | Por qué no se eligió |
|---|---|
| Servicio en la nube (Render, Railway, Fly.io) | Planes gratuitos con límites de horas y de memoria; la base de datos gratuita suele expirar. Es una opción válida si el equipo la prefiere. |
| Azure for Students o AWS Academy | Requieren configurar redes, credenciales de nube e identidades; más tiempo de configuración que de aprendizaje del pipeline. |
| Despliegue por SSH desde un runner de GitHub | Exige abrir el puerto SSH de la máquina a Internet y guardar una llave privada en Secrets. El runner autohospedado solo hace conexiones salientes hacia GitHub. |
| Kubernetes (kind o minikube) | Es una bonificación del proyecto. Agrega manifiestos, probes y políticas; se deja como reto opcional. |

## Consecuencias

- El entorno es accesible para el equipo y para la demostración, pero depende de que la máquina esté encendida.
- El runner ejecuta código del repositorio en esa máquina. Por eso: solo los jobs de `main`, de tags y de la reversión manual usan el runner; los Pull Requests corren en runners de GitHub; y en un repositorio público se exige aprobación para los workflows de Pull Requests de colaboradores externos (ver la amenaza A7 en `docs/seguridad.md`).
- La prueba de carga corre contra el entorno real y deja reservas canceladas en la base de datos. Con dos entornos (pruebas y producción) se correría contra el de pruebas.
- Se despliega con una ventana corta sin servicio mientras se recrea el contenedor (no hay despliegue azul-verde).
