package com.example.items.app.controller;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.items.app.dto.ItemDto;
import com.example.items.app.mapper.ItemDtoMapper;
import com.example.items.domain.model.Item;
import com.example.items.domain.model.Product;
import com.example.items.domain.services.ItemService;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

	
    private static final Logger logger = LoggerFactory.getLogger(ItemController.class);

    //private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;
	private final ItemService itemService;
	private final ItemDtoMapper mapper;



	@GetMapping
	public List<ItemDto> findAll() {
		return itemService.findAll().stream()
			.map(mapper::toDto)
			.toList();
	}


	/* @GetMapping("/{id}")
	public ItemDto findById(@PathVariable Long id) {
		// el id del circuit breaker debe ser el mismo que el del servicio 
		// que se quiere proteger, en este caso "itemService".
		// es el mismo id que se usa en application.yml para configurar el circuit breaker.
		return circuitBreakerFactory.create("itemService").run(
			() -> itemService.findById(id), 
			(e -> fallbackItem(id, e))
		)
		.map(mapper::toDto)
		.orElseThrow(() -> new EntityNotFoundException("Item not found with id: %s", id));
	} */


	// Usamos la anotación @CircuitBreaker y TimeLimiter para proteger el método findById, 
	// indicando el nombre del circuit breaker y el método de fallback que se ejecutará 
	// en caso de error o timeout.	
	// Esto es analogo al metodo anterior, pero usando la anotación en lugar de la API 
	// programática del CircuitBreakerFactory.
	// El name del circuit breaker debe ser el mismo que el del servicio que se quiere proteger,
	// en este caso "itemService", y el mismo que se usa en application.yml para
	// configurar el circuit breaker y el time limiter.

	@CircuitBreaker(name = "itemService", fallbackMethod = "fallbackItem")
	@TimeLimiter(name = "itemService", fallbackMethod = "fallbackItem")
	@GetMapping("/{id}")
	public ItemDto findById(@PathVariable Long id) {
		return itemService.findById(id)
			.map(mapper::toDto)
			.orElseThrow(() -> new EntityNotFoundException("Item not found with id: %s", id));
	}


	// el fallback debe tener la misma firma que el método protegido, pero con un parámetro adicional 
	// de tipo Throwable para recibir la excepción que causó el fallback.
	private Optional<Item> fallbackItem(Long id, Throwable throwable) {
		// return a default item when: 
		// 1) the circuit breaker is open, or
		// 2) an error occurs while fetching the item
		logger.error("Fallback for item with id: {}", id, throwable);
		return Optional.of(new Item(new Product(
			id, 
			"Default Product on error "+throwable.getMessage(), 
			BigDecimal.ZERO, 
			Instant.now(), 
			Instant.now()
		), 0));
	}

}
