# Seguridad del servicio de reservas

Este documento tiene tres partes: el modelo de amenazas (STRIDE), los controles de seguridad del pipeline con sus umbrales y el registro de hallazgos.

## 1. Componentes y flujos analizados

```mermaid
flowchart LR
    cliente["Cliente HTTP<br/>(recepción de la biblioteca)"]
    subgraph servidor["Máquina del equipo (Docker Compose)"]
        app["app<br/>API de reservas<br/>:8090"]
        db[("postgres<br/>127.0.0.1:5433")]
        runner["Runner autohospedado<br/>(etiqueta dyas)"]
    end
    gh["GitHub Actions"]
    ghcr["GHCR<br/>ghcr.io/&lt;usuario&gt;/&lt;repo&gt;"]

    cliente -- "F1: HTTPS/HTTP + Basic" --> app
    app -- "F2: JDBC" --> db
    gh -- "F3: trabajos del pipeline" --> runner
    runner -- "F4: docker pull" --> ghcr
    runner -- "F5: docker compose up" --> app
```

## 2. Modelo de amenazas (STRIDE)

| ID | Categoría STRIDE | Componente o flujo | Amenaza | Mitigación | Cómo se verifica |
|---|---|---|---|---|---|
| A1 | Suplantación (S) | F1, `app` | Alguien sin credenciales crea o cancela reservas, o se hace pasar por un gestor. | Autenticación HTTP Basic en toda la API; claves de al menos 12 caracteres que llegan por variables de entorno desde GitHub Secrets; el autor de la reserva se toma del usuario autenticado (`registradaPor`), no del cuerpo de la petición. | `ReservaControllerTest` (`sinCredencialesRespondeNoAutorizado`, `conUnaClaveIncorrectaRespondeNoAutorizado`); paso 3 de `scripts/smoke-test.sh` (espera 401 en cada despliegue). |
| A2 | Manipulación (T) | F1 → F2 | Datos de entrada manipulados: inyección SQL, horarios fuera de rango, cuerpos incompletos. | Consultas de Spring Data con parámetros (sin concatenar SQL); validación de formato con Bean Validation; reglas de negocio en el dominio; la API solo acepta `application/json`. | SAST con CodeQL (job `sast`); `ReservaTest`; `rechazaUnCuerpoIncompletoSinLlamarAlCasoDeUso`; `soloAceptaJson`. |
| A3 | Manipulación (T) | F4, imagen | Se despliega una imagen alterada o con una dependencia vulnerable (cadena de suministro). | Imágenes base con versión fija; Trivy sobre dependencias e imagen; Dependabot; se despliega la etiqueta `sha-...` de la imagen que se escaneó; herramientas de seguridad con versión fija. | Jobs `sca` e `imagen`; paso *Quality gate de la imagen* del job `publicar`. |
| A4 | Repudio (R) | `app` | Un gestor niega haber creado o cancelado una reserva. | Cada reserva guarda quién la registró (`registradaPor`); el registro `auditoria` escribe acción, reserva y usuario en cada creación y cancelación. | `elGestorCreaUnaReservaYQuedaRegistradoComoAutor`; `creaGuardaEnPostgresYConsultaUnaReserva` (`ReservasApiIT`). Límite: los logs viven en el contenedor (ver H-06). |
| A5 | Divulgación de información (I) | repositorio, imagen, F1 | Una contraseña o token queda en el código, en el historial o en la imagen; un error muestra la traza de la pila; Actuator expone variables de entorno. | Gitleaks sobre todo el historial; `.env` en `.gitignore` y en `.dockerignore`; configuración solo por variables de entorno; `server.error.include-stacktrace=never`; Actuator solo expone `health` e `info`; consultar reservas (que tienen correos) exige autenticación. | Job `secretos`; `lasRutasNoDeclaradasQuedanBloqueadas` (`/actuator/env` responde 403). |
| A6 | Denegación de servicio (D) | `app`, `postgres` | Un pico de peticiones o una consulta lenta deja el servicio sin responder. | Límites de CPU y memoria en `docker-compose.yml`; la consulta de reservas es de un solo día y usa índice; el SLO se verifica con carga en cada despliegue; reinicio automático (`restart: unless-stopped`). | `scripts/slo.sh` (k6, p95 < 500 ms, errores < 1 %) en el job `desplegar`. Límite: no hay limitación de tasa por cliente (ver H-07). |
| A7 | Elevación de privilegios (E) | `app`, runner | (a) Un atacante que logra ejecutar código en la aplicación toma control del contenedor o de la máquina. (b) Un Pull Request de un repositorio externo (fork) ejecuta código en el runner autohospedado. (c) Un usuario de consulta intenta crear reservas. | (a) Usuario sin privilegios (UID 10001), `cap_drop: ALL`, `no-new-privileges`, sistema de archivos de solo lectura. (b) Los Pull Requests corren en runners de GitHub; el runner solo atiende jobs de `main`, tags y la reversión manual dentro del environment `produccion`; en repositorios públicos se exige aprobación para workflows de colaboradores externos. (c) Autorización por roles (CONSULTA no puede escribir). | `docker compose exec app id` (uid=10001); `elRolConsultaPuedeListarPeroNoCrear` (403); en `cd.yml` solo los jobs `desplegar` y `revertir` usan `self-hosted`, y `cd.yml` no se dispara con `pull_request`. |
| A8 | Divulgación de información (I) | F2, `postgres` | La base de datos queda expuesta en la red. | El puerto de PostgreSQL solo se publica en `127.0.0.1`; la aplicación se conecta por la red interna de Compose. | `docker compose ps` muestra `127.0.0.1:5433->5432/tcp`. |

## 3. Controles del pipeline

Los cuatro controles funcionan como *quality gates*: si encuentran un problema por encima del umbral, el job falla y el Pull Request no se puede fusionar. Se pueden ejecutar en local con los mismos scripts que usa el pipeline.

| Control | Herramienta | Script / job | Umbral mínimo | Umbral de este repositorio |
|---|---|---|---|---|
| Análisis estático (SAST) | CodeQL | `scripts/umbral-sast.sh` / `sast` | Ningún hallazgo alto o crítico sin tratar | `security-severity` ≥ 7.0 sin registro en este documento |
| Análisis de dependencias (SCA) | Trivy `fs` + Dependabot | `scripts/sca.sh` / `sca` | Ninguna vulnerabilidad alta o crítica con corrección | HIGH o CRITICAL con corrección (`--severity HIGH,CRITICAL --ignore-unfixed`) |
| Detección de secretos | Gitleaks | `scripts/secretos.sh` / `secretos` | Cero secretos, incluido el historial | Cero secretos en todos los commits (`gitleaks git`) |
| Escaneo de la imagen | Trivy `image` | `scripts/escaneo-imagen.sh` / `imagen` y `publicar` | Ninguna vulnerabilidad crítica con corrección | CRITICAL con corrección (`--severity CRITICAL --ignore-unfixed`) |

Cómo se registra una excepción:

- **CodeQL:** una línea `<regla> <archivo>` en `.github/codeql/excepciones-sast.txt`.
- **Trivy:** el identificador de la vulnerabilidad en `.trivyignore`.
- **Gitleaks:** la huella (*fingerprint*) del hallazgo en `.gitleaksignore`, solo para falsos positivos. Un secreto real nunca se ignora: se rota y se elimina.

Toda excepción debe aparecer en la tabla siguiente. Una excepción sin registro, o un umbral relajado, cuenta como un control no implementado.

## 4. Registro de hallazgos

| ID | Control | Hallazgo | Decisión | Justificación o corrección |
|---|---|---|---|---|
| H-01 | SAST (CodeQL) | `java/spring-disabled-csrf-protection` (severidad 8.8) en `SeguridadConfig.java`: la protección CSRF está desactivada. | Riesgo aceptado | La API no usa sesiones ni cookies (`SessionCreationPolicy.STATELESS`) y solo acepta `application/json`: un formulario de otro sitio no puede enviar ese tipo de contenido sin una solicitud previa de CORS, y CORS no está habilitado. El riesgo que queda es que un navegador que guardó las credenciales Basic las reenvíe. **Para corregirlo:** reemplazar HTTP Basic por tokens Bearer (OAuth 2.0 / OpenID Connect con el proveedor de identidad de la universidad). Registrado en `.github/codeql/excepciones-sast.txt`. |
| H-02 | Prueba de carga (SLO) | Con BCrypt de costo 10 (valor por defecto), cada petición autenticada tarda decenas de milisegundos en verificar la clave y la prueba de carga incumple el SLO (p95 de varios segundos a 20 iteraciones por segundo). | Riesgo aceptado | Se usa BCrypt de costo 4 (`SeguridadConfig.COSTO_BCRYPT`). Los hashes solo existen en memoria: las claves llegan por variables de entorno y nunca se guardan en disco ni en la base de datos, así que un costo alto no protege contra el robo de una base de hashes. **Para corregirlo:** autenticación con tokens, que se validan sin recalcular un hash en cada petición. |
| H-03 | SCA (Trivy) | `tomcat-embed-core` 11.0.24, incluida por Spring Boot 4.1.1: tres vulnerabilidades CRITICAL con corrección (CVE-2026-65182, CVE-2026-65905, CVE-2026-68525). | Corregido | Se fija `<tomcat.version>11.0.26</tomcat.version>` en `pom.xml` (commit `60aeab1`). Se quita cuando una versión de Spring Boot ya traiga la corrección. |
| H-04 | SCA (Trivy) | `jackson-core` y `jackson-databind` 3.1.5: cinco vulnerabilidades HIGH de denegación de servicio con corrección (CVE-2026-89407, CVE-2026-89425, CVE-2026-68497, CVE-2026-91776, CVE-2026-91777). | Corregido | Se fija `<jackson-bom.version>3.1.7</jackson-bom.version>` en `pom.xml` (commit `60aeab1`). |
| H-05 | Imagen (Trivy) | Vulnerabilidades MEDIUM y LOW en paquetes de Ubuntu 24.04 de la imagen base `eclipse-temurin`, sin corrección publicada. | Riesgo aceptado | Están por debajo del umbral y no tienen corrección disponible. Dependabot propone las nuevas versiones de la imagen base; el reporte completo queda en el artefacto `reporte-imagen`. |
| H-06 | Modelo de amenazas (A4) | El registro de auditoría vive en los logs del contenedor y se pierde al recrearlo. | Riesgo aceptado | Para un sistema académico de un solo servidor es suficiente con `docker compose logs`. **Para corregirlo:** centralizar los logs (Loki o ELK) o guardar la cancelación y su autor en la base de datos. |
| H-07 | Modelo de amenazas (A6) | No hay limitación de tasa por cliente. | Riesgo aceptado | El servicio no está expuesto a Internet en el entorno del equipo. **Para corregirlo:** un proxy inverso (Traefik o Nginx) con *rate limiting* delante de la aplicación. |

## 5. Evidencia de que los controles funcionan

Cada control se probó con un cambio que introduce el problema a propósito. En el repositorio del proyecto, cada uno de estos cambios se hace en una rama y se abre un Pull Request que el pipeline bloquea y que **no se fusiona**. El paso a paso está en la sección *Demostrar que los controles funcionan* del README.

| Control | Cambio | Resultado esperado |
|---|---|---|
| SCA e imagen | Agregar `org.apache.commons:commons-text:1.9` al `pom.xml` | `CVE-2022-42889` CRITICAL; fallan los jobs `sca` e `imagen` |
| Secretos | Agregar una llave inventada a `application.properties` | Gitleaks reporta `generic-api-key`; falla `secretos`, también después de borrar la línea en otro commit |
| SAST | Borrar la línea de H-01 en `.github/codeql/excepciones-sast.txt` | `[PENDIENTE] 8.8 java/spring-disabled-csrf-protection`; falla `sast` |
| Arquitectura | Usar `org.springframework.util.StringUtils` dentro de `Reserva` | ArchUnit reporta la violación de la regla 2; falla `build-pruebas` |
