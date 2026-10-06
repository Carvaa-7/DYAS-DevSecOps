package edu.unisabana.dyas.reservas.configuracion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Autenticación (HTTP Basic) y autorización por roles de la API.
 *
 * <ul>
 *   <li>Rol CONSULTA: puede consultar reservas.</li>
 *   <li>Rol GESTOR: puede además crear y cancelar reservas.</li>
 * </ul>
 * Las credenciales llegan por variables de entorno; el código no tiene valores por defecto.
 */
@Configuration
public class SeguridadConfig {

    static final int LONGITUD_MINIMA_CLAVE = 12;

    /**
     * Costo de BCrypt. Con HTTP Basic la clave se verifica en CADA petición, así que el costo se paga
     * en la latencia. Con el valor por defecto (10) la prueba de carga incumple el SLO (p95 de varios segundos).
     * Los hashes solo existen en memoria (las claves llegan por variables de entorno y nunca se guardan),
     * por eso un costo alto no protege nada aquí. Ver el hallazgo H-02 en docs/seguridad.md.
     */
    static final int COSTO_BCRYPT = 4;

    @Bean
    PasswordEncoder codificadorDeClaves() {
        return new BCryptPasswordEncoder(COSTO_BCRYPT);
    }

    @Bean
    SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(reglas -> reglas
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/reservas/**").hasAnyRole("CONSULTA", "GESTOR")
                        .requestMatchers(HttpMethod.POST, "/api/reservas/**").hasRole("GESTOR")
                        .anyRequest().denyAll())
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // API sin sesiones ni cookies que solo acepta JSON. Ver el hallazgo H-01 en docs/seguridad.md.
                .csrf(AbstractHttpConfigurer::disable);
        return http.build();
    }

    @Bean
    UserDetailsService usuarios(PasswordEncoder codificador,
                                @Value("${app.seguridad.gestor.usuario}") String gestor,
                                @Value("${app.seguridad.gestor.clave}") String claveGestor,
                                @Value("${app.seguridad.consulta.usuario}") String consulta,
                                @Value("${app.seguridad.consulta.clave}") String claveConsulta) {
        validarClave(gestor, claveGestor);
        validarClave(consulta, claveConsulta);
        return new InMemoryUserDetailsManager(
                User.withUsername(gestor).password(codificador.encode(claveGestor)).roles("GESTOR").build(),
                User.withUsername(consulta).password(codificador.encode(claveConsulta)).roles("CONSULTA").build());
    }

    private static void validarClave(String usuario, String clave) {
        if (clave == null || clave.length() < LONGITUD_MINIMA_CLAVE) {
            throw new IllegalStateException("La clave del usuario " + usuario + " debe tener al menos "
                    + LONGITUD_MINIMA_CLAVE + " caracteres");
        }
    }
}
