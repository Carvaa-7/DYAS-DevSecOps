package edu.unisabana.dyas.reservas.adaptadores.salida.persistencia;

import edu.unisabana.dyas.reservas.aplicacion.puertos.salida.RepositorioReservas;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Adaptador de salida: implementa el puerto {@link RepositorioReservas} con JPA y PostgreSQL. */
@Repository
public class RepositorioReservasJpa implements RepositorioReservas {

    private final ReservaJpaRepository jpa;

    RepositorioReservasJpa(ReservaJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Reserva guardar(Reserva reserva) {
        return jpa.save(ReservaEntidad.desde(reserva)).aDominio();
    }

    @Override
    public Optional<Reserva> buscarPorId(UUID id) {
        return jpa.findById(id).map(ReservaEntidad::aDominio);
    }

    @Override
    public List<Reserva> buscarPorSalaYFecha(String sala, LocalDate fecha) {
        return jpa.findBySalaAndFecha(sala, fecha).stream().map(ReservaEntidad::aDominio).toList();
    }

    @Override
    public List<Reserva> buscarPorFecha(LocalDate fecha) {
        return jpa.findByFechaOrderBySalaAscInicioAsc(fecha).stream().map(ReservaEntidad::aDominio).toList();
    }
}
