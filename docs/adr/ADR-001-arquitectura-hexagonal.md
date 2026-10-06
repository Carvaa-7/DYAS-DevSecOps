# ADR-001. Arquitectura hexagonal para el servicio de reservas

**Estado:** aceptada

## Contexto

El servicio de reservas de salas debe poder cambiar de mecanismo de entrada (actualmente una API REST; en el futuro, por ejemplo, un bot o una cola de mensajes) y de almacenamiento (actualmente PostgreSQL) sin reescribir las reglas de negocio: horario de la biblioteca, duración máxima, correo institucional y cruces entre reservas.

Además, las reglas de negocio deben poder probarse de forma aislada, sin base de datos ni servidor web.

## Decisión

Se usa una arquitectura hexagonal (puertos y adaptadores) con estos paquetes:

| Paquete | Contenido | Puede depender de |
|---|---|---|
| `dominio` | `Reserva` y las excepciones de negocio. Java puro. | Solo Java estándar |
| `aplicacion` | Casos de uso (`ServicioReservas`) y puertos: `GestionarReservas` (entrada) y `RepositorioReservas` (salida) | `dominio` y Java estándar |
| `adaptadores.entrada.web` | Controlador REST, DTO y traducción de errores | `aplicacion`, `dominio`, Spring Web |
| `adaptadores.salida.persistencia` | Entidad JPA y repositorio que implementa `RepositorioReservas` | `aplicacion`, `dominio`, JPA |
| `configuracion` | Raíz de composición: crea los casos de uso y la seguridad | Todas las capas |

## Reglas que se verifican en cada build

Estas reglas están escritas como pruebas de ArchUnit en `src/test/java/.../ArquitecturaTest.java` y detienen el pipeline si se violan:

1. El dominio no depende de `aplicacion`, `adaptadores` ni `configuracion`.
2. El dominio no depende de Spring, JPA, Hibernate, Bean Validation ni Jackson.
3. La capa de aplicación solo depende del dominio, de sus puertos y de Java estándar.
4. Los adaptadores dependen hacia adentro y no se conocen entre sí (regla `onionArchitecture` de ArchUnit).

## Alternativas consideradas

- **Arquitectura en capas (controlador, servicio, repositorio).** Más simple, pero el servicio suele terminar dependiendo de JPA y las reglas de negocio quedan mezcladas con la persistencia.
- **Sin separación (todo en el controlador).** Descartada: impide probar las reglas sin levantar Spring.

## Consecuencias

- Las reglas de negocio se prueban con pruebas unitarias rápidas y sin Spring (`ReservaTest`, `ServicioReservasTest`).
- Hay más clases (entidad JPA separada del modelo de dominio, puertos, mapeos).
- La validación de reservas que se cruzan y el guardado no ocurren en la misma transacción: dos peticiones simultáneas podrían crear reservas cruzadas. Es un límite conocido; la corrección sería una restricción de exclusión en PostgreSQL o un bloqueo por sala.
