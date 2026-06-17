package com.example.auth.passwordgrant;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationGrantAuthenticationToken;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Representa una petición de token via el grant type "password" (ROPC).
 *
 * Spring Authorization Server eliminó el password grant en OAuth 2.1 por razones de seguridad,
 * pero sigue siendo útil en entornos de test: permite que herramientas como Azertio obtengan
 * un token de usuario real con una sola llamada HTTP, sin necesidad de simular el flujo de
 * redirección del authorization_code (que requiere navegador).
 *
 * Esta clase es necesaria porque el pipeline de autenticación de Spring Authorization Server
 * está basado en tipos: el Converter produce un token de un tipo concreto, y el Provider
 * declara que soporta ese mismo tipo. Sin esta clase intermedia no hay forma de conectarlos.
 *
 * Uso desde cualquier cliente HTTP:
 *   POST /oauth2/token
 *   grant_type=password&username=admin&password=admin&scope=openid&client_id=...&client_secret=...
 */
public class PasswordGrantAuthenticationToken extends OAuth2AuthorizationGrantAuthenticationToken {

    public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

    private final String username;
    private final String password;
    private final Set<String> scopes;

    public PasswordGrantAuthenticationToken(Authentication clientPrincipal, String username, String password,
                                            Set<String> scopes, Map<String, Object> additionalParameters) {
        super(PASSWORD, clientPrincipal, additionalParameters);
        this.username = username;
        this.password = password;
        this.scopes = Collections.unmodifiableSet(scopes);
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Set<String> getScopes() { return scopes; }
}
