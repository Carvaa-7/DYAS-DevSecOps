# Pipeline de entrega continua y segura

Este documento describe el pipeline del servicio de reservas: qué etapas tiene, qué verifica cada una, qué umbral la detiene y por qué se eligió ese valor. También muestra la vista de despliegue y el procedimiento de reversión.

## 1. Diagrama del pipeline

```mermaid
flowchart LR
    pr["Push a una rama<br/>o Pull Request"] --> ci
    main["Push a main<br/>o tag vX.Y.Z"] --> ci

    subgraph ci["ci.yml"]
        direction TB
        build["build-pruebas<br/>construcción · ArchUnit ·<br/>unitarias · integración · cobertura"]
        sast["sast<br/>CodeQL + umbral"]
        sca["sca<br/>Trivy fs"]
        sec["secretos<br/>Gitleaks (historial)"]
        img["imagen<br/>docker build + Trivy image"]
        build --> img
    end

    ci -->|"solo en main o tag"| pub["publicar<br/>build · Trivy · push a GHCR<br/>(versión + sha)"]
    pub --> dep["desplegar (runner autohospedado)<br/>deploy.sh · smoke-test.sh · slo.sh"]
    dep -->|"si falla"| rb["rollback.sh<br/>versión anterior"]
    manual["Actions > CD > Run workflow<br/>(tag)"] --> rev["revertir<br/>rollback.sh &lt;tag&gt;"]
```

- En un Pull Request corre solo `ci.yml`. Sus jobs son los *checks* obligatorios del ruleset de `main`.
- En `main` (o al crear un tag `vX.Y.Z`) corre `cd.yml`, que primero ejecuta `ci.yml` completo (`workflow_call`) y solo si todo pasa publica y despliega.
- Los jobs `desplegar` y `revertir` solo corren si la variable del repositorio `DESPLIEGUE` vale `activo`.

## 2. Etapas y umbrales

| Etapa (enunciado) | Job / paso | Herramienta | Detiene el pipeline cuando... | Por qué ese valor |
|---|---|---|---|---|
| Construcción | `build-pruebas` / *Construcción* | Maven (`mvn compile`) | No compila o no se resuelven las dependencias. | Sin código que compile no tiene sentido seguir. |
| Reglas de arquitectura | `build-pruebas` / *Reglas de arquitectura* | ArchUnit (`ArquitecturaTest`) | Se viola una de las cuatro reglas de dependencias de ADR-001. | Las reglas protegen las fronteras de la arquitectura hexagonal; una sola violación ya acopla el dominio a la infraestructura. |
| Pruebas unitarias | `build-pruebas` / *Pruebas...* | JUnit 5 + Mockito (surefire) | Falla una prueba o la cobertura de líneas del paquete `dominio` baja de **80 %**. | El enunciado exige al menos 70 %. Se usa 80 % porque el dominio es pequeño y concentra las reglas de negocio. |
| Pruebas de integración | `build-pruebas` / *Pruebas...* | Testcontainers con PostgreSQL 18.6 (failsafe) | Falla una prueba. | Verifica la frontera con la base de datos real, con la misma versión que el despliegue. |
| Análisis de seguridad: código | `sast` | CodeQL + `scripts/umbral-sast.sh` | Hay un hallazgo con severidad ≥ 7.0 sin tratar. | Umbral mínimo del enunciado (alto o crítico). |
| Análisis de seguridad: dependencias | `sca` | Trivy `fs` | Hay una vulnerabilidad HIGH o CRITICAL con corrección disponible. | Umbral mínimo del enunciado. Si hay corrección, actualizar es responsabilidad del equipo. |
| Análisis de seguridad: secretos | `secretos` | Gitleaks | Aparece un secreto en cualquier commit. | Umbral mínimo del enunciado: cero secretos, incluido el historial. |
| Imagen y escaneo | `imagen` y `publicar` | Docker + Trivy `image` | Hay una vulnerabilidad CRITICAL con corrección disponible. | Umbral mínimo del enunciado. El sistema base tiene vulnerabilidades sin corrección que no dependen del equipo (H-05). |
| Despliegue | `desplegar` / *Desplegar con Docker Compose* | `scripts/deploy.sh` | El contenedor no llega a *healthy* en 180 s. | Tiempo holgado para que arranque la JVM y se conecte a PostgreSQL. |
| Verificación: prueba de humo | `desplegar` / *Prueba de humo* | `scripts/smoke-test.sh` | Health no está UP, la versión no es la desplegada, la API no exige autenticación o falla el flujo crear, consultar y cancelar. | Comprueba lo mínimo para decir que la versión nueva funciona. |
| Verificación: SLO | `desplegar` / *Prueba de carga del SLO* | k6 (`perf/slo.js`) | p95 ≥ **500 ms** o errores ≥ **1 %** con 20 iteraciones por segundo durante 30 s. | Es el SLO del servicio: una consulta de reservas debe sentirse inmediata. La prueba es corta para no alargar el pipeline; la prueba larga se lanza a mano. |

Todos los reportes (pruebas, cobertura, CodeQL, Trivy, Gitleaks y k6) se publican como artefactos de cada ejecución. Las dependencias de Maven quedan en caché (`setup-java` con `cache: maven`), de modo que el pipeline de integración tarde menos de 10 minutos.

## 3. Vista de despliegue

```mermaid
flowchart TB
    subgraph github["GitHub"]
        actions["GitHub Actions<br/>(runners ubuntu-latest)"]
        ghcr["GitHub Container Registry<br/>ghcr.io/&lt;usuario&gt;/&lt;repo&gt;:1.0.0 y :sha-xxxxxxx"]
        secrets["Environment produccion<br/>Secrets y Variables"]
    end

    subgraph maquina["Máquina del equipo (Linux con Docker Engine)"]
        runner["Runner autohospedado<br/>etiqueta: dyas"]
        subgraph compose["Proyecto Compose: reservas (red reservas_default)"]
            app["Contenedor app<br/>imagen ghcr.io/...:sha-xxxxxxx<br/>puerto 8090 → 8080<br/>usuario 10001, solo lectura"]
            pg[("Contenedor postgres<br/>postgres:18.6-alpine<br/>127.0.0.1:5433 → 5432<br/>volumen datos-postgres")]
            k6["Contenedor k6 (temporal)<br/>grafana/k6:2.3.0"]
        end
    end

    usuario["Cliente HTTP"] -->|"HTTP :8090"| app
    app -->|"JDBC :5432"| pg
    k6 -->|"HTTP app:8080"| app
    actions -->|"push de la imagen"| ghcr
    runner -->|"consulta trabajos (HTTPS saliente)"| actions
    runner -->|"docker pull"| ghcr
    secrets -.->|"variables de entorno del job"| runner
    runner -->|"docker compose up -d --wait"| compose
```

El runner solo abre conexiones salientes hacia GitHub: no hace falta abrir puertos de la máquina a Internet para desplegar.

## 4. Configuración del despliegue

| Nombre | Tipo (environment `produccion`) | Uso |
|---|---|---|
| `POSTGRES_PASSWORD` | Secret | Clave de PostgreSQL |
| `GESTOR_CLAVE` | Secret | Clave del usuario con rol GESTOR |
| `CONSULTA_CLAVE` | Secret | Clave del usuario con rol CONSULTA |
| `POSTGRES_DB`, `POSTGRES_USER` | Variable | Nombre de la base de datos y su usuario |
| `GESTOR_USUARIO`, `CONSULTA_USUARIO` | Variable | Nombres de los usuarios de la API |
| `APP_URL` | Variable | URL del sistema desplegado (aparece en la ejecución del pipeline) |
| `DESPLIEGUE` | Variable **del repositorio** | `activo` para habilitar los jobs del runner autohospedado |

`GITHUB_TOKEN` lo crea GitHub en cada ejecución. Solo el job `publicar` tiene permiso `packages: write`; `desplegar` y `revertir` tienen `packages: read`; el resto solo `contents: read`.

## 5. Reversión

El script `scripts/deploy.sh` guarda en `~/.reservas-despliegue/` qué imagen estaba desplegada antes de cada despliegue.

| Situación | Qué pasa |
|---|---|
| Falla el despliegue, la prueba de humo o el SLO | El paso *Reversión automática* ejecuta `scripts/rollback.sh`, que vuelve a la imagen anterior y repite la prueba de humo. El pipeline queda en rojo. |
| Se detecta un problema después | Actions → CD → *Run workflow* con el tag de la imagen (por ejemplo `sha-1a2b3c4` o `1.0.0`). El job `revertir` la despliega y ejecuta la prueba de humo. |
| Sin GitHub (en la máquina) | `scripts/rollback.sh ghcr.io/<usuario>/<repo>:<tag>` con las variables del `.env`. |

Los tags anteriores se consultan en la página del paquete en GitHub (*Packages*) o en el resumen de cada ejecución del job `publicar`.

La reversión cambia la imagen, no los datos: el esquema de la base de datos debe ser compatible con la versión anterior (en este servicio, `schema.sql` solo crea tablas e índices si no existen).
