package com.example.gateway.app.controller;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.Map;

// IMPLEMENTACIÓN MANUAL DEL FLUJO OAUTH2 — solo con fines educativos.
//
// Esta clase muestra cómo funciona internamente el intercambio de authorization code
// por tokens. En producción NO se hace esto: spring-boot-starter-oauth2-client
// (junto con SecurityConfig.java y oauth2Login()) lo gestiona automáticamente.
//
// Flujo que implementa esta clase manualmente:
//   1. El usuario hace login en el auth-server
//   2. El auth-server redirige al navegador a /authorized?code=ABC123
//   3. Este endpoint recibe el code y llama a /oauth2/token (POST) para canjearlo
//   4. El auth-server devuelve access_token, id_token y refresh_token
//
// Con oauth2Login() (enfoque profesional):
//   - Spring Security registra /login/oauth2/code/gateway como redirect_uri
//   - Hace el intercambio de code → tokens internamente
//   - Guarda los tokens en la sesión y propaga el access_token con TokenRelay=
//
// Para activar la implementación manual:
//   1. Eliminar SecurityConfig.java (o comentar oauth2Login)
//   2. Descomentar el @RestController y el @GetMapping de abajo
//   3. Cambiar el redirect_uri en el navegador a /authorized en vez de /login/oauth2/code/gateway

// @RestController  // <-- descomentar para usar el flujo manual
public class AuthorizationController {

    // Credenciales del cliente OAuth2 registrado en el auth-server,
    // codificadas en Base64 para el header Authorization: Basic
    private static final String CLIENT_CREDENTIALS =
        Base64.getEncoder().encodeToString("gateway:secret".getBytes());

    private static final String TOKEN_URI = "http://localhost:9100/oauth2/token";
    private static final String REDIRECT_URI = "http://localhost:8090/authorized";

    private final WebClient webClient;

    public AuthorizationController(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    // El auth-server redirige al navegador aquí con el code como parámetro:
    //   GET /authorized?code=ABC123
    // Este método intercambia ese code por los tokens llamando a /oauth2/token.
    // @GetMapping("/authorized")  // <-- descomentar para usar el flujo manual
    public Mono<Map<String, Object>> authorized(@RequestParam String code) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("code", code);
        formData.add("redirect_uri", REDIRECT_URI);

        return webClient.post()
            .uri(TOKEN_URI)
            .header(HttpHeaders.AUTHORIZATION, "Basic " + CLIENT_CREDENTIALS)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<>() {});
    }
}