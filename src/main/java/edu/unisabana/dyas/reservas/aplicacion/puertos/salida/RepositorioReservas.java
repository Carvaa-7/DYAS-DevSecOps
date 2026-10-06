package edu.unisabana.dyas.reservas.aplicacion.puertos.salida;

import edu.unisabana.dyas.reservas.dominio.Reserva;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Puerto de salida: lo que la aplicación necesita de la persistencia, sin decir cómo se implementa. */
public interface RepositorioReservas {

    Reserva guardar(Reserva reserva);

    Optional<Reserva> buscarPorId(UUID id);

    List<Reserva> buscarPorSalaYFecha(String sala, LocalDate fecha);

    List<Reserva> buscarPorFecha(LocalDate fecha);
}
