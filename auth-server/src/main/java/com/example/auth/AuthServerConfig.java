package com.example.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.security.KeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
public class AuthServerConfig {

    // ------------------------------------------------------------------------------------
    // 1a. SEGURIDAD HTTP DEL SERVIDOR DE AUTORIZACIÓN (prioridad alta)
    //
    // Este FilterChain protege los endpoints propios del servidor OAuth2/OIDC:
    //   /oauth2/authorize, /oauth2/token, /oauth2/jwks, /userinfo, etc.
    //
    // Solo se aplica a esas rutas (securityMatcher). Las demás rutas van al
    // segundo FilterChain (ver más abajo).
    // ------------------------------------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer =
            OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
            // Limita este FilterChain solo a los endpoints OAuth2/OIDC
            .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
            // Aplica la configuración del servidor de autorización y activa soporte OIDC
            // OIDC (OpenID Connect) añade el endpoint /userinfo y el scope "openid"
            .with(authorizationServerConfigurer, authorizationServer ->
                authorizationServer.oidc(Customizer.withDefaults()))
            // Todos los endpoints de este chain requieren autenticación.
            // Sin esto, un usuario anónimo en /oauth2/authorize no lanza AccessDeniedException
            // y la petición llega al servlet sin respuesta, acabando en /error.
            .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
            // Si un navegador (HTML) accede sin autenticarse, redirige al login
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
            // El endpoint /userinfo actúa como resource server: valida el Access Token JWT
            .oauth2ResourceServer(resourceServer -> resourceServer
                .jwt(Customizer.withDefaults()));

        return http.build();
    }


    // ------------------------------------------------------------------------------------
    // 1b. SEGURIDAD HTTP PARA EL RESTO DE RUTAS (formulario de login, etc.)
    //
    // Este segundo FilterChain captura todo lo que no interceptó el primero.
    // Habilita el formulario de login estándar de Spring Security en /login,
    // que es donde el primer FilterChain redirige cuando el usuario no está autenticado.
    // ------------------------------------------------------------------------------------
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults());
        return http.build();
    }


    // ------------------------------------------------------------------------------------
    // 2. USUARIOS QUE PUEDEN HACER LOGIN EN ESTE SERVIDOR
    //
    // Estos son los usuarios finales (personas) que se autentican en el formulario
    // de login. En producción vendría de base de datos, LDAP, etc.
    // {noop} indica que la contraseña no está cifrada (solo para desarrollo).
    // ------------------------------------------------------------------------------------
    @Bean
    public UserDetailsService userDetailsService() {
        UserDetails userDetails = User.withUsername("user")
            .password("{noop}user")
            .roles("USER")
            .build();
        UserDetails adminDetails = User.withUsername("admin")
            .password("{noop}admin")
            .roles("USER", "ADMIN")
            .build();
        return new InMemoryUserDetailsManager(userDetails, adminDetails);
    }


    // ------------------------------------------------------------------------------------
    // 3. CLIENTES OAUTH2 REGISTRADOS
    //
    // Un "cliente" en OAuth2 es una aplicación externa (p.ej. tu app web o móvil)
    // que quiere usar este servidor para autenticar a sus usuarios.
    //
    // Cada cliente tiene:
    //   - clientId / clientSecret: credenciales de la aplicación (no del usuario)
    //   - authorizationGrantType: flujos OAuth2 permitidos
    //       AUTHORIZATION_CODE = el flujo estándar con redirección al navegador
    //       REFRESH_TOKEN      = permite renovar tokens sin que el usuario vuelva a logarse
    //   - redirectUri: whitelist de URLs permitidas tras autorizar. El auth server NO llama
    //       a esta URL — es el NAVEGADOR quien la sigue (302). Su función es de seguridad:
    //       evita que un atacante sustituya la URI por la suya y robe el authorization code.
    //       Si el cliente corre en un puerto distinto hay que registrar una URI adicional.
    //       En producción cada app/entorno tiene su propio cliente con sus propias URIs.
    //   - scope "openid"/"profile": los scopes que este cliente tiene permitido solicitar.
    //       Solo habilitan la posibilidad — el cliente los pide explícitamente en cada petición.
    //
    // PATRÓN BFF (Backend for Frontend):
    //   Es habitual registrar el GATEWAY como único cliente OAuth2, con las redirectUri
    //   apuntando a su puerto. El gateway gestiona todo el flujo de login y los microservicios
    //   internos solo reciben tokens ya validados — nunca participan en el flujo OAuth2.
    //   Esto combina con que cada microservicio sea un resource server que valida el JWT:
    //     - Gateway    → cliente OAuth2 (gestiona login, intercambia codes por tokens)
    //     - Servicios  → resource servers (validan el token en cada petición interna)
    //
    // En producción esto se guardaría en base de datos.
    // ------------------------------------------------------------------------------------
    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient oidcClient = RegisteredClient.withId(UUID.randomUUID().toString())
            .clientId("gateway")
            .clientSecret("{noop}secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .redirectUri("http://localhost:8090/login/oauth2/code/gateway")
            // URI adicional para el flujo de login con Spring Security, que redirige a /authorized tras el login
            // En producción habría que registrar la URI de cada entorno (dev, staging, prod) y cada app cliente.
            .redirectUri("http://localhost:8090/authorized")
            // URI a la que redirige el navegador tras cerrar sesión (logout)
            // En producción habría que registrar la URI de cada entorno (dev, staging, prod) y cada app cliente.
            .postLogoutRedirectUri("http://localhost:8090/logged-out")
            .scope(OidcScopes.OPENID)
            .scope(OidcScopes.PROFILE)
            // muestra pantalla de consentimiento tras el login para que el usuario
            // apruebe explícitamente los scopes que está concediendo a la app cliente
            // En producción se suele desactivar para que el usuario no tenga que aprobar cada vez.
            .clientSettings(ClientSettings.builder()
                .requireAuthorizationConsent(false)
                .build())
            .build();
        return new InMemoryRegisteredClientRepository(oidcClient);
    }


    // ------------------------------------------------------------------------------------
    // 4. CLAVE RSA PARA FIRMAR LOS JWT
    //
    // Los tokens JWT que emite este servidor van firmados con una clave RSA privada.
    // Los resource servers (otras APIs) verifican esa firma usando la clave pública,
    // que se publica en el endpoint /.well-known/jwks.json (JWK Set).
    //
    // ATENCIÓN: aquí la clave se genera en memoria al arrancar. Esto significa que
    // cada reinicio invalida todos los tokens existentes. En producción la clave
    // debe persistirse (p.ej. en un KeyStore o en un secret manager).
    // ------------------------------------------------------------------------------------
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
            .privateKey(privateKey)
            .keyID(UUID.randomUUID().toString()) // identificador de la clave en el JWK Set
            .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    // Necesario para que el endpoint /userinfo pueda validar el Access Token recibido
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }


    // ------------------------------------------------------------------------------------
    // 5. CONFIGURACIÓN GENERAL DEL SERVIDOR
    //
    // Permite personalizar las URLs de los endpoints OAuth2 y el issuer (emisor).
    // Si no se especifica issuer, usa automáticamente la URL base del servidor.
    // ------------------------------------------------------------------------------------
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
            //.issuer("http://localhost:9000")
            .build();
    }


    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
        return context -> {
            if (!context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)) return;
            Authentication principal = context.getPrincipal();
            // client_credentials no tiene usuario real — sus "authorities" son scopes, no roles
            if (!(principal instanceof UsernamePasswordAuthenticationToken)) return;
            context.getClaims().claim(
                "roles",
                principal.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .toList()
            );
        };
    }

    // Genera un par de claves RSA de 2048 bits al arrancar la aplicación
    private static KeyPair generateRsaKey() {
        try {
            java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}