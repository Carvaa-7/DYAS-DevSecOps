package edu.unisabana.dyas.reservas.adaptadores.salida.persistencia;

import edu.unisabana.dyas.reservas.dominio.EstadoReserva;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** Fila de la tabla reservas. Es un detalle del adaptador: el dominio no la conoce. */
@Entity
@Table(name = "reservas")
public class ReservaEntidad {

    @Id
    private UUID id;

    @Column(nullable = false, length = 10)
    private String sala;

    @Column(nullable = false, length = 120)
    private String responsable;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime inicio;

    @Column(nullable = false)
    private LocalTime fin;

    @Column(name = "registrada_por", nullable = false, length = 60)
    private String registradaPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoReserva estado;

    protected ReservaEntidad() {
        // requerido por JPA
    }

    static ReservaEntidad desde(Reserva reserva) {
        ReservaEntidad fila = new ReservaEntidad();
        fila.id = reserva.getId();
        fila.sala = reserva.getSala();
        fila.responsable = reserva.getResponsable();
        fila.fecha = reserva.getFecha();
        fila.inicio = reserva.getInicio();
        fila.fin = reserva.getFin();
        fila.registradaPor = reserva.getRegistradaPor();
        fila.estado = reserva.getEstado();
        return fila;
    }

    Reserva aDominio() {
        return Reserva.reconstruir(id, sala, responsable, fecha, inicio, fin, registradaPor, estado);
    }
}
