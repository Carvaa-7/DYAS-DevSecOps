package edu.unisabana.dyas.reservas;

import edu.unisabana.dyas.reservas.aplicacion.puertos.salida.RepositorioReservas;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración: la aplicación completa (web + casos de uso + JPA) contra un PostgreSQL real
 * que Testcontainers levanta en un contenedor. Verifica las fronteras con la infraestructura.
 */
@SpringBootTest(properties = {
        "GESTOR_USUARIO=gestor",
        "GESTOR_CLAVE=clave-de-prueba-gestor",
        "CONSULTA_USUARIO=consulta",
        "CONSULTA_CLAVE=clave-de-prueba-consulta"
})
@AutoConfigureMockMvc
@Testcontainers
class ReservasApiIT {

    /** La misma versión de PostgreSQL que usa docker-compose.yml. */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RepositorioReservas repositorio;

    private static String cuerpo(String sala, LocalDate fecha, String inicio, String fin) {
        return """
                {"sala":"%s","responsable":"ana.perez@unisabana.edu.co",
                 "fecha":"%s","inicio":"%s","fin":"%s"}""".formatted(sala, fecha, inicio, fin);
    }

    private MvcResult crear(String json, int estadoEsperado) throws Exception {
        return mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(json)
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().is(estadoEsperado))
                .andReturn();
    }

    @Test
    void creaGuardaEnPostgresYConsultaUnaReserva() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(3);
        String location = crear(cuerpo("D-301", fecha, "08:00", "10:00"), 201).getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        assertThat(repositorio.buscarPorId(id)).isPresent()
                .get().satisfies(r -> assertThat(r.getRegistradaPor()).isEqualTo("gestor"));

        mvc.perform(get(location).with(httpBasic("consulta", "clave-de-prueba-consulta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sala").value("D-301"))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));
    }

    @Test
    void laBaseDeDatosDevuelveLosCrucesYLaApiRespondeConflicto() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(4);
        crear(cuerpo("D-302", fecha, "10:00", "12:00"), 201);

        crear(cuerpo("D-302", fecha, "11:00", "13:00"), 409);

        mvc.perform(get("/api/reservas").param("fecha", fecha.toString())
                        .with(httpBasic("consulta", "clave-de-prueba-consulta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void cancelarActualizaElEstadoEnLaBaseDeDatos() throws Exception {
        LocalDate fecha = LocalDate.now().plusDays(5);
        String location = crear(cuerpo("D-303", fecha, "14:00", "15:00"), 201).getResponse().getHeader("Location");

        mvc.perform(post(location + "/cancelacion").with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        crear(cuerpo("D-303", fecha, "14:00", "15:00"), 201);
    }

    @Test
    void elHealthcheckEstaDisponibleSinCredenciales() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.version").value("local"));
    }
}
