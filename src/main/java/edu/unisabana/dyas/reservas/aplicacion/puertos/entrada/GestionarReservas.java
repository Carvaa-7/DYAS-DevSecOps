package edu.unisabana.dyas.reservas.aplicacion.puertos.entrada;

import edu.unisabana.dyas.reservas.dominio.Reserva;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Puerto de entrada: los casos de uso que ofrece la aplicación a los adaptadores de entrada. */
public interface GestionarReservas {

    Reserva crear(NuevaReserva nueva);

    Reserva consultar(UUID id);

    List<Reserva> listarPorFecha(LocalDate fecha);

    Reserva cancelar(UUID id);
}
