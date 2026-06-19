package com.example.items.infra.clients.webclient;

import com.example.items.domain.spi.ProductService;
import com.example.items.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clients.products.type", havingValue = "webclient")
public class ProductServiceWebClient implements ProductService {

    private final WebClient.Builder clientBuilder;


    @Override
    public List<ProductDto> findAll() {
        return clientBuilder.build().get()
            .uri("http://product-service/products")
            .accept(MediaType.APPLICATION_JSON)
            .headers(this::addBearerToken)
            .retrieve()
            .bodyToFlux(ProductDto.class)
            .collectList()
            .block();
    }

    @Override
    public ProductDto findById(Long id) {
        return clientBuilder.build().get()
            .uri("http://product-service/products/{id}", id)
            .accept(MediaType.APPLICATION_JSON)
            .headers(this::addBearerToken)
            .retrieve()
            .bodyToMono(ProductDto.class)
            .block();
    }

    // Propaga el Bearer token del contexto de seguridad actual al microservicio destino.
    // El token llega a items-service via TokenRelay del gateway y debe reenviarse
    // a product-service para que pueda validar la autenticación.
    private void addBearerToken(HttpHeaders headers) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            headers.setBearerAuth(jwtAuth.getToken().getTokenValue());
        }
    }

}
