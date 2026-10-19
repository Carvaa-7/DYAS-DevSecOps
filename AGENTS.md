# Instrucciones para agentes de código

Este archivo lo leen los asistentes y agentes de programación (Copilot, Codex, Cursor, Claude Code y otros que siguen la convención `AGENTS.md`) antes de modificar el repositorio. Resume las decisiones de arquitectura y de seguridad que el pipeline verifica. Si un agente las ignora, el pipeline lo detiene: estas instrucciones evitan que proponga cambios que después van a fallar.

## El sistema

API REST de reservas de salas de estudio. Java 21, Spring Boot 4.1, PostgreSQL, Maven. Arquitectura hexagonal (puertos y adaptadores), documentada en `docs/adr/ADR-001-arquitectura-hexagonal.md`.

## Cómo verificar un cambio

Antes de dar un cambio por terminado, ejecuta lo mismo que el pipeline:

```sh
mvn -B verify                                                          # pruebas unitarias, integración y cobertura
mvn -B test -Dtest=ArquitecturaTest -Dsurefire.failIfNoSpecifiedTests=false   # reglas de arquitectura
scripts/sca.sh                                                         # dependencias con vulnerabilidades
scripts/secretos.sh                                                    # secretos en el historial
```

Las pruebas de integración necesitan Docker en ejecución (Testcontainers).

## Reglas de arquitectura (no se negocian)

Las verifica `src/test/java/edu/unisabana/dyas/reservas/ArquitecturaTest.java`. Si una regla falla, cambia el diseño del código, nunca la regla.

1. `dominio` es Java puro: no usa Spring, JPA, Hibernate, Bean Validation ni Jackson, y no conoce `aplicacion`, `adaptadores` ni `configuracion`.
2. `aplicacion` solo usa el dominio, sus propios puertos y `java.*`. Los casos de uso hablan con el exterior a través de los puertos de `aplicacion.puertos`.
3. Los adaptadores (`adaptadores.entrada.web`, `adaptadores.salida.persistencia`) dependen hacia adentro y no se conocen entre sí.
4. `configuracion` es la raíz de composición: es el único paquete que conecta casos de uso con adaptadores.

Una regla de negocio nueva va en el dominio, con su prueba unitaria en `src/test/java/.../dominio/`. Un acceso a datos nuevo empieza por un método en el puerto `RepositorioReservas` y después se implementa en el adaptador de persistencia.

## Reglas de seguridad

- Ningún secreto en el código, en la configuración ni en los mensajes de commit. Toda clave llega por variables de entorno (`application.properties` usa `${...}` sin valor por defecto).
- No bajes umbrales: cobertura del dominio (`jacoco.cobertura.dominio` en `pom.xml`), SLO (`perf/slo.js`), severidades de `scripts/sca.sh`, `scripts/escaneo-imagen.sh` y `scripts/umbral-sast.sh`.
- No agregues excepciones a `.trivyignore`, `.gitleaksignore` ni `.github/codeql/excepciones-sast.txt`. Una excepción solo la decide una persona y queda registrada en `docs/seguridad.md`.
- No agregues dependencias sin comprobar que existen en Maven Central con ese `groupId`, `artifactId` y versión. Prefiere las que ya gestiona Spring Boot.
- No cambies los permisos (`permissions:`) de los workflows, no uses el runner autohospedado (`runs-on: self-hosted`) en jobs que se ejecuten en Pull Requests y no despliegues imágenes etiquetadas como `latest`.
- Las consultas a la base de datos usan Spring Data con parámetros. Nunca construyas SQL concatenando texto.

## Convenciones

- Nombres de clases, métodos y variables en español, como el resto del código.
- Pruebas con el patrón Arrange, Act, Assert y nombres que describen el comportamiento (`rechazaFechasPasadas`, `exigeCorreoInstitucional`).
- Los errores de la API se responden con Problem Details (RFC 9457) y mensajes en español, sin la traza de la pila.
- Un cambio de arquitectura (un paquete nuevo, una tecnología nueva, otra forma de desplegar) necesita un ADR nuevo en `docs/adr/` antes del código.
