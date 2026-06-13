package com.example.gateway;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

// Configuración de seguridad del gateway como cliente OAuth2 y resource server.
//
// Soporta dos modos de autenticación en paralelo:
//   - oauth2Login(): flujo de navegador — redirige al auth-server, gestiona sesión
//   - oauth2ResourceServer(): flujo API — acepta Bearer token en el header Authorization
//
// El segundo modo permite que clientes automatizados (tests, CI, herramientas CLI)
// obtengan un token via client_credentials y lo pasen directamente sin sesión.
// Spring Security evalúa primero si hay Bearer token; si no, usa la sesión de login.
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/logged-out").permitAll()
                .anyExchange().authenticated())
            .oauth2Login(Customizer.withDefaults())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }
}
