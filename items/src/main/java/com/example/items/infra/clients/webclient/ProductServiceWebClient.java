package com.example.items.infra.clients.webclient;

import com.example.items.domain.spi.ProductService;
import com.example.items.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
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
		// bloquea hasta que se complete la solicitud y se obtenga la lista de productos, lo cual es necesario para
		// cumplir con la firma del método que devuelve una lista de ProductDto.
		// Esto rompe la naturaleza reactiva de WebClient, pero es necesario para adaptarse a la interfaz
		// ProductService que espera una lista sincrónica.
		return clientBuilder.build().get().uri("http://product-service/products")// nombre del servicio y ruta, NO LA URI REAL
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToFlux(ProductDto.class)
			.collectList()
			.block();
	}

	@Override
	public ProductDto findById(Long id) {
		return clientBuilder.build().get().uri("http://product-service/products/{id}", id)
			.accept(MediaType.APPLICATION_JSON)
			.retrieve()
			.bodyToMono(ProductDto.class)
			.block();
	}

}
