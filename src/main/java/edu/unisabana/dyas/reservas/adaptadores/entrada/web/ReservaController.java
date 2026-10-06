package edu.unisabana.dyas.reservas.adaptadores.entrada.web;

import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.GestionarReservas;
import edu.unisabana.dyas.reservas.dominio.Reserva;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Adaptador de entrada HTTP: traduce peticiones REST a llamadas al puerto de entrada {@link GestionarReservas}. */
@RestController
@RequestMapping("/api/reservas")
public class ReservaController {

    /** Registro de auditoría: quién creó o canceló cada reserva (mitigación de repudio). */
    private static final Logger AUDITORIA = LoggerFactory.getLogger("auditoria");

    private final GestionarReservas reservas;

    public ReservaController(GestionarReservas reservas) {
        this.reservas = reservas;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ReservaRespuesta> crear(@Valid @RequestBody SolicitudReserva solicitud,
                                                  Authentication autenticacion) {
        Reserva reserva = reservas.crear(solicitud.aComando(autenticacion.getName()));
        AUDITORIA.info("accion=crear reserva={} usuario={}", reserva.getId(), autenticacion.getName());
        return ResponseEntity.created(URI.create("/api/reservas/" + reserva.getId()))
                .body(ReservaRespuesta.de(reserva));
    }

    @GetMapping("/{id}")
    public ReservaRespuesta consultar(@PathVariable UUID id) {
        return ReservaRespuesta.de(reservas.consultar(id));
    }

    @GetMapping
    public List<ReservaRespuesta> listar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return reservas.listarPorFecha(fecha).stream().map(ReservaRespuesta::de).toList();
    }

    @PostMapping("/{id}/cancelacion")
    public ReservaRespuesta cancelar(@PathVariable UUID id, Authentication autenticacion) {
        Reserva reserva = reservas.cancelar(id);
        AUDITORIA.info("accion=cancelar reserva={} usuario={}", reserva.getId(), autenticacion.getName());
        return ReservaRespuesta.de(reserva);
    }
}
