package edu.unisabana.dyas.reservas.aplicacion.puertos.entrada;

import java.time.LocalDate;
import java.time.LocalTime;

/** Datos para crear una reserva (comando del caso de uso). */
public record NuevaReserva(String sala, String responsable, LocalDate fecha, LocalTime inicio, LocalTime fin,
                           String registradaPor) {
}
