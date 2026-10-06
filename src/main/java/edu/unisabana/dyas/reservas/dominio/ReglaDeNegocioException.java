package edu.unisabana.dyas.reservas.dominio;

/** Excepción base del dominio: una regla de negocio no se cumple. */
public abstract class ReglaDeNegocioException extends RuntimeException {

    protected ReglaDeNegocioException(String mensaje) {
        super(mensaje);
    }
}
