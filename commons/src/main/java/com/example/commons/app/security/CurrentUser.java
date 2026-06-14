package com.example.commons.app.security;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Acceso al usuario autenticado en el hilo actual (Spring MVC).
 *
 * Requiere que el microservicio tenga spring-boot-starter-oauth2-resource-server
 * y valide el JWT entrante — de lo contrario getJwt() lanzará ClassCastException.
 *
 * Alternativa WebFlux (reactiva):
 *
 *   import org.springframework.security.core.context.ReactiveSecurityContextHolder;
 *   import reactor.core.publisher.Mono;
 *
 *   public Mono<String> getUsername() {
 *       return ReactiveSecurityContextHolder.getContext()
 *           .map(ctx -> (Jwt) ctx.getAuthentication().getPrincipal())
 *           .map(Jwt::getSubject);
 *   }
 *
 *   public Mono<List<String>> getRoles() {
 *       return ReactiveSecurityContextHolder.getContext()
 *           .map(ctx -> (Jwt) ctx.getAuthentication().getPrincipal())
 *           .map(jwt -> jwt.getClaimAsStringList("roles"));
 *   }
 */
@Component
public class CurrentUser {

    public String getUsername() {
        return getJwt().getSubject();
    }

    public String getEmail() {
        return getJwt().getClaimAsString("email");
    }

    public List<String> getRoles() {
        List<String> roles = getJwt().getClaimAsStringList("roles");
        return roles != null ? roles : List.of();
    }

    public boolean hasRole(String role) {
        return getRoles().contains(role);
    }

    private Jwt getJwt() {
        return (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
