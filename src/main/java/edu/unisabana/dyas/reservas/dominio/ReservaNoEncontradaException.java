package edu.unisabana.dyas.reservas.dominio;

import java.util.UUID;

/** No existe una reserva con el identificador indicado. */
public class ReservaNoEncontradaException extends ReglaDeNegocioException {

    public ReservaNoEncontradaException(UUID id) {
        super("No existe la reserva " + id);
    }
}
