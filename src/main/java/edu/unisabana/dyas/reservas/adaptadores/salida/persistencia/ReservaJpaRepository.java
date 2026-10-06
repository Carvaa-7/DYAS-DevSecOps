package edu.unisabana.dyas.reservas.adaptadores.salida.persistencia;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Repositorio de Spring Data. Las consultas se generan con parámetros, sin concatenar SQL. */
interface ReservaJpaRepository extends JpaRepository<ReservaEntidad, UUID> {

    List<ReservaEntidad> findBySalaAndFecha(String sala, LocalDate fecha);

    List<ReservaEntidad> findByFechaOrderBySalaAscInicioAsc(LocalDate fecha);
}
