package edu.unisabana.dyas.reservas.adaptadores.entrada.web;

import edu.unisabana.dyas.reservas.dominio.EstadoReserva;
import edu.unisabana.dyas.reservas.dominio.Reserva;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Representación JSON de una reserva. */
public record ReservaRespuesta(UUID id, String sala, String responsable, LocalDate fecha, LocalTime inicio,
                               LocalTime fin, EstadoReserva estado, String registradaPor) {

    static ReservaRespuesta de(Reserva reserva) {
        return new ReservaRespuesta(reserva.getId(), reserva.getSala(), reserva.getResponsable(),
                reserva.getFecha(), reserva.getInicio(), reserva.getFin(), reserva.getEstado(),
                reserva.getRegistradaPor());
    }
}
