package edu.unisabana.dyas.reservas.dominio;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReservaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 6);
    private static final LocalDate MANANA = HOY.plusDays(1);
    private static final String CORREO = "ana.perez@unisabana.edu.co";

    private static Reserva reserva(String sala, LocalDate fecha, String inicio, String fin) {
        return Reserva.nueva(sala, CORREO, fecha, LocalTime.parse(inicio), LocalTime.parse(fin), "gestor", HOY);
    }

    @Nested
    class Creacion {

        @Test
        void creaUnaReservaActivaConLosDatosRecibidos() {
            Reserva r = reserva("B-201", MANANA, "10:00", "12:00");

            assertThat(r.getId()).isNotNull();
            assertThat(r.getSala()).isEqualTo("B-201");
            assertThat(r.getResponsable()).isEqualTo(CORREO);
            assertThat(r.getFecha()).isEqualTo(MANANA);
            assertThat(r.getInicio()).isEqualTo(LocalTime.of(10, 0));
            assertThat(r.getFin()).isEqualTo(LocalTime.of(12, 0));
            assertThat(r.getRegistradaPor()).isEqualTo("gestor");
            assertThat(r.getEstado()).isEqualTo(EstadoReserva.ACTIVA);
            assertThat(r.estaActiva()).isTrue();
        }

        @Test
        void permiteReservarParaElMismoDia() {
            assertThat(reserva("B-201", HOY, "07:00", "08:00").getFecha()).isEqualTo(HOY);
        }

        @Test
        void permiteUsarTodaLaFranjaHastaElCierre() {
            Reserva r = reserva("B-201", MANANA, "18:00", "21:00");
            assertThat(r.getFin()).isEqualTo(Reserva.CIERRE);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"b-201", "B201", "B-20", "SALA-1", "B-2011"})
        void rechazaCodigosDeSalaMalFormados(String sala) {
            assertThatThrownBy(() -> reserva(sala, MANANA, "10:00", "11:00"))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("formato");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"ana@gmail.com", "ana.perez@unisabana.edu", "@unisabana.edu.co"})
        void exigeCorreoInstitucional(String correo) {
            assertThatThrownBy(() -> Reserva.nueva("B-201", correo, MANANA, LocalTime.of(10, 0),
                    LocalTime.of(11, 0), "gestor", HOY))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("@unisabana.edu.co");
        }

        @Test
        void rechazaFechasPasadas() {
            assertThatThrownBy(() -> reserva("B-201", HOY.minusDays(1), "10:00", "11:00"))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("pasada");
        }

        @Test
        void exigeFechaYHoras() {
            assertThatThrownBy(() -> Reserva.nueva("B-201", CORREO, null, LocalTime.of(10, 0),
                    LocalTime.of(11, 0), "gestor", HOY))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("obligatorias");
        }

        @ParameterizedTest(name = "{0} - {1}")
        @CsvSource({"11:00, 10:00", "10:00, 10:00"})
        void exigeQueElInicioSeaAnteriorAlFin(String inicio, String fin) {
            assertThatThrownBy(() -> reserva("B-201", MANANA, inicio, fin))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("anterior");
        }

        @ParameterizedTest(name = "{0} - {1}")
        @CsvSource({"06:30, 08:00", "20:00, 21:30"})
        void rechazaHorariosFueraDeLaJornada(String inicio, String fin) {
            assertThatThrownBy(() -> reserva("B-201", MANANA, inicio, fin))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("entre las");
        }

        @Test
        void rechazaReservasDeMasDeTresHoras() {
            assertThatThrownBy(() -> reserva("B-201", MANANA, "08:00", "11:30"))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("3 horas");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void exigeSaberQuienRegistraLaReserva(String usuario) {
            assertThatThrownBy(() -> Reserva.nueva("B-201", CORREO, MANANA, LocalTime.of(10, 0),
                    LocalTime.of(11, 0), usuario, HOY))
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("registró");
        }
    }

    @Nested
    class Cancelacion {

        @Test
        void cancelaUnaReservaActiva() {
            Reserva r = reserva("B-201", MANANA, "10:00", "11:00");

            r.cancelar();

            assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(r.estaActiva()).isFalse();
        }

        @Test
        void noPermiteCancelarDosVeces() {
            Reserva r = reserva("B-201", MANANA, "10:00", "11:00");
            r.cancelar();

            assertThatThrownBy(r::cancelar)
                    .isInstanceOf(ReservaInvalidaException.class)
                    .hasMessageContaining("ya estaba cancelada");
        }
    }

    @Nested
    class Cruces {

        private final Reserva existente = reserva("B-201", MANANA, "10:00", "12:00");

        @ParameterizedTest(name = "{0} - {1} se cruza con 10:00 - 12:00")
        @CsvSource({"09:00, 10:30", "11:00, 13:00", "10:00, 12:00", "10:30, 11:30", "09:30, 12:30"})
        void detectaCrucesEnLaMismaSalaYFecha(String inicio, String fin) {
            assertThat(reserva("B-201", MANANA, inicio, fin).seCruzaCon(existente)).isTrue();
        }

        @ParameterizedTest(name = "{0} - {1} no se cruza con 10:00 - 12:00")
        @CsvSource({"08:00, 10:00", "12:00, 13:00"})
        void reservasContiguasNoSeCruzan(String inicio, String fin) {
            assertThat(reserva("B-201", MANANA, inicio, fin).seCruzaCon(existente)).isFalse();
        }

        @Test
        void otraSalaUOtraFechaNoSeCruzan() {
            assertThat(reserva("B-202", MANANA, "10:00", "12:00").seCruzaCon(existente)).isFalse();
            assertThat(reserva("B-201", MANANA.plusDays(1), "10:00", "12:00").seCruzaCon(existente)).isFalse();
        }

        @Test
        void unaReservaCanceladaNoOcupaLaSala() {
            Reserva cancelada = reserva("B-201", MANANA, "10:00", "12:00");
            cancelada.cancelar();

            assertThat(reserva("B-201", MANANA, "10:00", "12:00").seCruzaCon(cancelada)).isFalse();
            assertThat(cancelada.seCruzaCon(existente)).isFalse();
        }
    }

    @Test
    void reconstruyeUnaReservaGuardadaSinCambiarSusDatos() {
        UUID id = UUID.randomUUID();

        Reserva r = Reserva.reconstruir(id, "C-101", CORREO, HOY.minusDays(10), LocalTime.of(8, 0),
                LocalTime.of(9, 0), "gestor", EstadoReserva.CANCELADA);

        assertThat(r.getId()).isEqualTo(id);
        assertThat(r.getFecha()).isEqualTo(HOY.minusDays(10));
        assertThat(r.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
    }
}
