package com.example.gateway.app;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
public class MeController {

    @GetMapping("/api/me")
    public Mono<Map<String, Object>> me(Authentication authentication) {
        if (authentication instanceof OAuth2AuthenticationToken token) {
            // flujo navegador: hay sesión OIDC con datos de perfil completos
            OidcUser user = (OidcUser) token.getPrincipal();
            return Mono.just(Map.of(
                "sub",   user.getSubject(),
                "name",  user.getFullName()  != null ? user.getFullName()  : "",
                "email", user.getEmail()     != null ? user.getEmail()     : ""
            ));
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            // flujo API: Bearer token con claims del usuario
            Jwt jwt = token.getToken();
            String sub = jwt.getSubject();
            // client_credentials no representa un usuario real
            if (jwt.getClaim("email") == null && jwt.getClaim("name") == null) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "No user session — /api/me requires a user token, not client_credentials");
            }
            return Mono.just(Map.of(
                "sub",   sub,
                "name",  jwt.getClaimAsString("name")  != null ? jwt.getClaimAsString("name")  : "",
                "email", jwt.getClaimAsString("email") != null ? jwt.getClaimAsString("email") : ""
            ));
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
}
