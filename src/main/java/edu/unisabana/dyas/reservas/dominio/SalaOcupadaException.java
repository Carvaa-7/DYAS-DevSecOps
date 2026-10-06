package edu.unisabana.dyas.reservas.dominio;

/** La sala ya tiene una reserva activa que se cruza con el horario pedido. */
public class SalaOcupadaException extends ReglaDeNegocioException {

    public SalaOcupadaException(String sala) {
        super("La sala " + sala + " ya está reservada en ese horario");
    }
}
