package edu.unisabana.dyas.reservas.adaptadores.entrada.web;

import edu.unisabana.dyas.reservas.dominio.ReservaInvalidaException;
import edu.unisabana.dyas.reservas.dominio.ReservaNoEncontradaException;
import edu.unisabana.dyas.reservas.dominio.SalaOcupadaException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones del dominio a respuestas HTTP con el formato Problem Details (RFC 9457).
 * Los errores no previstos los atiende Spring Boot con un 500 genérico, sin la traza de la pila.
 */
@RestControllerAdvice
public class ManejadorErrores {

    @ExceptionHandler(ReservaInvalidaException.class)
    public ProblemDetail reservaInvalida(ReservaInvalidaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(SalaOcupadaException.class)
    public ProblemDetail salaOcupada(SalaOcupadaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ReservaNoEncontradaException.class)
    public ProblemDetail noEncontrada(ReservaNoEncontradaException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
