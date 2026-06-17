package com.example.auth.passwordgrant;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.security.Principal;
import java.util.Set;

/**
 * Segunda parte del pipeline del password grant: recibe el PasswordGrantAuthenticationToken
 * producido por el Converter y realiza la autenticación real del usuario.
 *
 * Responsabilidades:
 *  1. Verificar que el cliente OAuth2 tiene el grant type "password" habilitado.
 *  2. Validar las credenciales del usuario (username/password) con DaoAuthenticationProvider,
 *     que a su vez usa el UserDetailsService y PasswordEncoder configurados.
 *  3. Generar el JWT de acceso usando el mismo JwtGenerator que el resto de flows
 *     (authorization_code, client_credentials), de modo que el tokenCustomizer añade
 *     los mismos claims personalizados (roles, email) independientemente del grant type.
 *  4. Persistir la autorización en el OAuth2AuthorizationService para que los tokens
 *     sean rastreables (útil para revocación).
 *
 * El método supports() es el mecanismo por el que el framework conecta este Provider
 * con el token producido por el Converter: solo se invoca cuando el token es de tipo
 * PasswordGrantAuthenticationToken.
 */
public class PasswordGrantAuthenticationProvider implements AuthenticationProvider {

    private final DaoAuthenticationProvider userAuthProvider;
    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;

    public PasswordGrantAuthenticationProvider(UserDetailsService userDetailsService,
                                               OAuth2AuthorizationService authorizationService,
                                               OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
                                               PasswordEncoder passwordEncoder) {
        this.userAuthProvider = new DaoAuthenticationProvider(userDetailsService);
        this.userAuthProvider.setPasswordEncoder(passwordEncoder);
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PasswordGrantAuthenticationToken grantAuth = (PasswordGrantAuthenticationToken) authentication;

        OAuth2ClientAuthenticationToken clientPrincipal = getClientPrincipal(grantAuth);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        if (!registeredClient.getAuthorizationGrantTypes().contains(PasswordGrantAuthenticationToken.PASSWORD)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        // Delega en el mismo mecanismo estándar que el formulario de login, evitando
        // duplicar la lógica de carga de usuario y comparación de contraseña
        Authentication userAuth;
        try {
            userAuth = userAuthProvider.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(grantAuth.getUsername(), grantAuth.getPassword())
            );
        } catch (org.springframework.security.core.AuthenticationException e) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        Set<String> authorizedScopes = resolveScopes(grantAuth.getScopes(), registeredClient);

        // El contexto le indica al JwtGenerator qué tipo de token generar y con qué principal,
        // de modo que el tokenCustomizer puede acceder al usuario autenticado para añadir
        // claims como "roles" y "email"
        DefaultOAuth2TokenContext tokenContext = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(userAuth)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorizationGrantType(PasswordGrantAuthenticationToken.PASSWORD)
                .authorizedScopes(authorizedScopes)
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .authorizationGrant(grantAuth)
                .build();

        OAuth2Token generatedToken = tokenGenerator.generate(tokenContext);
        if (generatedToken == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                generatedToken.getTokenValue(),
                generatedToken.getIssuedAt(),
                generatedToken.getExpiresAt(),
                authorizedScopes
        );

        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .principalName(userAuth.getName())
                .authorizationGrantType(PasswordGrantAuthenticationToken.PASSWORD)
                .authorizedScopes(authorizedScopes)
                .accessToken(accessToken)
                .attribute(Principal.class.getName(), userAuth)
                .build();
        authorizationService.save(authorization);

        return new OAuth2AccessTokenAuthenticationToken(registeredClient, clientPrincipal, accessToken);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PasswordGrantAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private Set<String> resolveScopes(Set<String> requested, RegisteredClient client) {
        if (requested.isEmpty()) {
            return client.getScopes();
        }
        for (String scope : requested) {
            if (!client.getScopes().contains(scope)) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_SCOPE);
            }
        }
        return requested;
    }

    private OAuth2ClientAuthenticationToken getClientPrincipal(PasswordGrantAuthenticationToken authentication) {
        if (authentication.getPrincipal() instanceof OAuth2ClientAuthenticationToken clientPrincipal
                && clientPrincipal.isAuthenticated()) {
            return clientPrincipal;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }
}
