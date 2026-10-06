package edu.unisabana.dyas.reservas.adaptadores.entrada.web;

import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.NuevaReserva;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

/** Cuerpo JSON de POST /api/reservas. Aquí se valida el formato; las reglas de negocio se validan en el dominio. */
public record SolicitudReserva(
        @NotBlank @Size(max = 10) String sala,
        @NotBlank @Email @Size(max = 120) String responsable,
        @NotNull LocalDate fecha,
        @NotNull LocalTime inicio,
        @NotNull LocalTime fin) {

    NuevaReserva aComando(String usuarioAutenticado) {
        return new NuevaReserva(sala, responsable, fecha, inicio, fin, usuarioAutenticado);
    }
}
