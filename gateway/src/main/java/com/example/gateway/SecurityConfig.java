package com.example.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

// Configuración de seguridad del gateway como cliente OAuth2.
//
// Con spring-boot-starter-oauth2-client y oauth2Login() activado, Spring Security:
//   1. Intercepta cualquier petición no autenticada y redirige al auth-server
//   2. Registra automáticamente el endpoint /login/oauth2/code/{registrationId}
//      que recibe el authorization code de vuelta (equivale al /authorized manual)
//   3. Intercambia el code por tokens y guarda la sesión del usuario
//
// El filtro TokenRelay= en las rutas propaga el access_token a los microservicios.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/logged-out").permitAll()
                .anyExchange().authenticated())
            .oauth2Login(Customizer.withDefaults())
            .build();
    }
}