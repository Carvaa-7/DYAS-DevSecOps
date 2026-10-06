package edu.unisabana.dyas.reservas.adaptadores;

import edu.unisabana.dyas.reservas.adaptadores.entrada.web.ReservaController;
import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.GestionarReservas;
import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.NuevaReserva;
import edu.unisabana.dyas.reservas.configuracion.SeguridadConfig;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import edu.unisabana.dyas.reservas.dominio.ReservaInvalidaException;
import edu.unisabana.dyas.reservas.dominio.ReservaNoEncontradaException;
import edu.unisabana.dyas.reservas.dominio.SalaOcupadaException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas del adaptador web: rutas, códigos HTTP, validación de entradas y reglas de autorización.
 * El caso de uso se reemplaza por un doble de prueba (Mockito).
 */
@WebMvcTest(ReservaController.class)
@Import(SeguridadConfig.class)
@TestPropertySource(properties = {
        "app.seguridad.gestor.usuario=gestor",
        "app.seguridad.gestor.clave=clave-de-prueba-gestor",
        "app.seguridad.consulta.usuario=consulta",
        "app.seguridad.consulta.clave=clave-de-prueba-consulta"
})
class ReservaControllerTest {

    private static final LocalDate FECHA = LocalDate.now().plusDays(1);
    private static final String CUERPO = """
            {"sala":"B-201","responsable":"ana.perez@unisabana.edu.co",
             "fecha":"%s","inicio":"10:00","fin":"12:00"}""".formatted(FECHA);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private GestionarReservas reservas;

    private static Reserva ejemplo() {
        return Reserva.nueva("B-201", "ana.perez@unisabana.edu.co", FECHA, LocalTime.of(10, 0),
                LocalTime.of(12, 0), "gestor", LocalDate.now());
    }

    @Test
    void sinCredencialesRespondeNoAutorizado() throws Exception {
        mvc.perform(get("/api/reservas").param("fecha", FECHA.toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(reservas);
    }

    @Test
    void conUnaClaveIncorrectaRespondeNoAutorizado() throws Exception {
        mvc.perform(get("/api/reservas").param("fecha", FECHA.toString())
                        .with(httpBasic("gestor", "otra-clave-cualquiera")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elRolConsultaPuedeListarPeroNoCrear() throws Exception {
        when(reservas.listarPorFecha(FECHA)).thenReturn(List.of(ejemplo()));

        mvc.perform(get("/api/reservas").param("fecha", FECHA.toString())
                        .with(httpBasic("consulta", "clave-de-prueba-consulta")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sala").value("B-201"));

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(httpBasic("consulta", "clave-de-prueba-consulta")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elGestorCreaUnaReservaYQuedaRegistradoComoAutor() throws Exception {
        when(reservas.crear(argThat(n -> n != null && "gestor".equals(n.registradaPor())))).thenReturn(ejemplo());

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/reservas/")))
                .andExpect(jsonPath("$.estado").value("ACTIVA"))
                .andExpect(jsonPath("$.registradaPor").value("gestor"));
    }

    @Test
    void rechazaUnCuerpoIncompletoSinLlamarAlCasoDeUso() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content("{\"sala\":\"B-201\"}")
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(reservas);
    }

    @Test
    void soloAceptaJson() throws Exception {
        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("sala=B-201")
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void traduceLasExcepcionesDelDominioACodigosHttp() throws Exception {
        when(reservas.crear(any(NuevaReserva.class)))
                .thenThrow(new SalaOcupadaException("B-201"))
                .thenThrow(new ReservaInvalidaException("No se puede reservar en una fecha pasada"));

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("La sala B-201 ya está reservada en ese horario"));

        mvc.perform(post("/api/reservas").contentType(MediaType.APPLICATION_JSON).content(CUERPO)
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultarUnaReservaInexistenteRespondeNoEncontrada() throws Exception {
        UUID id = UUID.randomUUID();
        when(reservas.consultar(id)).thenThrow(new ReservaNoEncontradaException(id));

        mvc.perform(get("/api/reservas/{id}", id).with(httpBasic("consulta", "clave-de-prueba-consulta")))
                .andExpect(status().isNotFound());
    }

    @Test
    void elGestorPuedeCancelar() throws Exception {
        Reserva cancelada = ejemplo();
        cancelada.cancelar();
        when(reservas.cancelar(cancelada.getId())).thenReturn(cancelada);

        mvc.perform(post("/api/reservas/{id}/cancelacion", cancelada.getId())
                        .with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void lasRutasNoDeclaradasQuedanBloqueadas() throws Exception {
        mvc.perform(get("/actuator/env").with(httpBasic("gestor", "clave-de-prueba-gestor")))
                .andExpect(status().isForbidden());
    }
}
