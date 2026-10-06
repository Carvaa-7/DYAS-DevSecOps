package edu.unisabana.dyas.reservas.dominio;

/** Los datos de la reserva no cumplen las reglas de negocio. */
public class ReservaInvalidaException extends ReglaDeNegocioException {

    public ReservaInvalidaException(String mensaje) {
        super(mensaje);
    }
}
