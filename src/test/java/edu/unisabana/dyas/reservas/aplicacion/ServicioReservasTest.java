package edu.unisabana.dyas.reservas.aplicacion;

import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.NuevaReserva;
import edu.unisabana.dyas.reservas.aplicacion.puertos.salida.RepositorioReservas;
import edu.unisabana.dyas.reservas.dominio.EstadoReserva;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import edu.unisabana.dyas.reservas.dominio.ReservaInvalidaException;
import edu.unisabana.dyas.reservas.dominio.ReservaNoEncontradaException;
import edu.unisabana.dyas.reservas.dominio.SalaOcupadaException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pruebas unitarias de los casos de uso. El puerto de salida se reemplaza por un repositorio en memoria:
 * no hace falta base de datos, ni Spring, ni Mockito.
 */
class ServicioReservasTest {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 6);
    private static final LocalDate MANANA = HOY.plusDays(1);

    private final RepositorioEnMemoria repositorio = new RepositorioEnMemoria();
    private final Clock reloj = Clock.fixed(HOY.atTime(9, 0).atZone(BOGOTA).toInstant(), BOGOTA);
    private final ServicioReservas servicio = new ServicioReservas(repositorio, reloj);

    private static NuevaReserva nueva(String sala, String inicio, String fin) {
        return new NuevaReserva(sala, "ana.perez@unisabana.edu.co", MANANA, LocalTime.parse(inicio),
                LocalTime.parse(fin), "gestor");
    }

    @Test
    void creaYGuardaUnaReserva() {
        Reserva creada = servicio.crear(nueva("B-201", "10:00", "12:00"));

        assertThat(repositorio.buscarPorId(creada.getId())).isPresent();
        assertThat(creada.getRegistradaPor()).isEqualTo("gestor");
    }

    @Test
    void usaElRelojInyectadoParaDecidirQueEsUnaFechaPasada() {
        NuevaReserva ayer = new NuevaReserva("B-201", "ana.perez@unisabana.edu.co", HOY.minusDays(1),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "gestor");

        assertThatThrownBy(() -> servicio.crear(ayer)).isInstanceOf(ReservaInvalidaException.class);
        assertThat(repositorio.todas()).isEmpty();
    }

    @Test
    void rechazaUnaReservaQueSeCruzaConOtraActiva() {
        servicio.crear(nueva("B-201", "10:00", "12:00"));

        assertThatThrownBy(() -> servicio.crear(nueva("B-201", "11:00", "13:00")))
                .isInstanceOf(SalaOcupadaException.class)
                .hasMessageContaining("B-201");
        assertThat(repositorio.todas()).hasSize(1);
    }

    @Test
    void permiteReservarElMismoHorarioEnOtraSala() {
        servicio.crear(nueva("B-201", "10:00", "12:00"));

        servicio.crear(nueva("B-202", "10:00", "12:00"));

        assertThat(repositorio.todas()).hasSize(2);
    }

    @Test
    void permiteVolverAReservarUnHorarioCancelado() {
        Reserva primera = servicio.crear(nueva("B-201", "10:00", "12:00"));
        servicio.cancelar(primera.getId());

        servicio.crear(nueva("B-201", "10:00", "12:00"));

        assertThat(repositorio.todas()).hasSize(2);
    }

    @Test
    void consultaUnaReservaExistente() {
        Reserva creada = servicio.crear(nueva("B-201", "10:00", "12:00"));

        assertThat(servicio.consultar(creada.getId()).getSala()).isEqualTo("B-201");
    }

    @Test
    void avisaCuandoLaReservaNoExiste() {
        UUID inexistente = UUID.randomUUID();

        assertThatThrownBy(() -> servicio.consultar(inexistente))
                .isInstanceOf(ReservaNoEncontradaException.class);
        assertThatThrownBy(() -> servicio.cancelar(inexistente))
                .isInstanceOf(ReservaNoEncontradaException.class);
    }

    @Test
    void cancelaYGuardaElNuevoEstado() {
        Reserva creada = servicio.crear(nueva("B-201", "10:00", "12:00"));

        servicio.cancelar(creada.getId());

        assertThat(repositorio.buscarPorId(creada.getId())).get()
                .extracting(Reserva::getEstado).isEqualTo(EstadoReserva.CANCELADA);
    }

    @Test
    void listaLasReservasDeUnaFecha() {
        servicio.crear(nueva("B-201", "10:00", "12:00"));
        servicio.crear(nueva("C-101", "08:00", "09:00"));

        assertThat(servicio.listarPorFecha(MANANA)).hasSize(2);
        assertThat(servicio.listarPorFecha(HOY)).isEmpty();
    }

    /** Implementación del puerto de salida para las pruebas. */
    private static final class RepositorioEnMemoria implements RepositorioReservas {

        private final Map<UUID, Reserva> datos = new LinkedHashMap<>();

        @Override
        public Reserva guardar(Reserva reserva) {
            datos.put(reserva.getId(), reserva);
            return reserva;
        }

        @Override
        public Optional<Reserva> buscarPorId(UUID id) {
            return Optional.ofNullable(datos.get(id));
        }

        @Override
        public List<Reserva> buscarPorSalaYFecha(String sala, LocalDate fecha) {
            return datos.values().stream()
                    .filter(r -> r.getSala().equals(sala) && r.getFecha().equals(fecha))
                    .toList();
        }

        @Override
        public List<Reserva> buscarPorFecha(LocalDate fecha) {
            return datos.values().stream().filter(r -> r.getFecha().equals(fecha)).toList();
        }

        List<Reserva> todas() {
            return new ArrayList<>(datos.values());
        }
    }
}
