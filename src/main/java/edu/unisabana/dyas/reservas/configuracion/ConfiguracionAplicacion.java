package edu.unisabana.dyas.reservas.configuracion;

import edu.unisabana.dyas.reservas.aplicacion.ServicioReservas;
import edu.unisabana.dyas.reservas.aplicacion.puertos.entrada.GestionarReservas;
import edu.unisabana.dyas.reservas.aplicacion.puertos.salida.RepositorioReservas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Raíz de composición: aquí se conectan los casos de uso con sus adaptadores.
 * Es el único paquete donde la capa de aplicación y Spring se encuentran.
 */
@Configuration
public class ConfiguracionAplicacion {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Bogota"));
    }

    @Bean
    GestionarReservas gestionarReservas(RepositorioReservas repositorio, Clock reloj) {
        return new ServicioReservas(repositorio, reloj);
    }
}
