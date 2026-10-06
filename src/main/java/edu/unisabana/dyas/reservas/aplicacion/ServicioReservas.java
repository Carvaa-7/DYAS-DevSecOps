package edu.unisabana.dyas.reservas.aplicacion;

import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.GestionarReservas;
import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.NuevaReserva;
import edu.unisabana.dyas.reservas.aplicacion.puertos.salida.RepositorioReservas;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import edu.unisabana.dyas.reservas.dominio.ReservaNoEncontradaException;
import edu.unisabana.dyas.reservas.dominio.SalaOcupadaException;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Casos de uso de las reservas. Solo depende del dominio y de los puertos:
 * no sabe si la reserva llegó por HTTP ni si se guarda en PostgreSQL.
 */
public class ServicioReservas implements GestionarReservas {

    private final RepositorioReservas repositorio;
    private final Clock reloj;

    public ServicioReservas(RepositorioReservas repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    @Override
    public Reserva crear(NuevaReserva nueva) {
        Reserva reserva = Reserva.nueva(nueva.sala(), nueva.responsable(), nueva.fecha(), nueva.inicio(),
                nueva.fin(), nueva.registradaPor(), LocalDate.now(reloj));
        boolean ocupada = repositorio.buscarPorSalaYFecha(reserva.getSala(), reserva.getFecha()).stream()
                .anyMatch(reserva::seCruzaCon);
        if (ocupada) {
            throw new SalaOcupadaException(reserva.getSala());
        }
        return repositorio.guardar(reserva);
    }

    @Override
    public Reserva consultar(UUID id) {
        return repositorio.buscarPorId(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    @Override
    public List<Reserva> listarPorFecha(LocalDate fecha) {
        return repositorio.buscarPorFecha(fecha);
    }

    @Override
    public Reserva cancelar(UUID id) {
        Reserva reserva = consultar(id);
        reserva.cancelar();
        return repositorio.guardar(reserva);
    }
}
