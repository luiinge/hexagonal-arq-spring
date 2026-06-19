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
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

    private static final Logger logger = LoggerFactory.getLogger(ItemController.class);

    private final ItemService itemService;
    private final ItemDtoMapper mapper;


    @GetMapping
    public List<ItemDto> findAll() {
        return itemService.findAll().stream()
            .map(mapper::toDto)
            .toList();
    }


    // @TimeLimiter requiere que el método devuelva CompletableFuture<T> para poder
    // imponer un límite de tiempo sobre la ejecución asíncrona.
    // Capturamos el SecurityContext antes de entrar al hilo asíncrono para que
    // el WebClient pueda propagar el Bearer token al llamar a product-service.
    @CircuitBreaker(name = "itemService", fallbackMethod = "fallbackItem")
    @TimeLimiter(name = "itemService", fallbackMethod = "fallbackItem")
    @GetMapping("/{id}")
    public CompletableFuture<ItemDto> findById(@PathVariable Long id) {
        SecurityContext context = SecurityContextHolder.getContext();
        return CompletableFuture.supplyAsync(() -> {
            SecurityContextHolder.setContext(context);
            try {
                return itemService.findById(id)
                    .map(mapper::toDto)
                    .orElseThrow(() -> new EntityNotFoundException("Item not found with id: %s", id));
            } finally {
                SecurityContextHolder.clearContext();
            }
        });
    }


    // El fallback debe tener la misma firma que el método protegido (mismo tipo de retorno
    // y mismos parámetros) más un Throwable al final.
    private CompletableFuture<ItemDto> fallbackItem(Long id, Throwable throwable) {
        logger.error("Fallback for item with id: {}", id, throwable.getMessage());
        Product product = new Product(
            id,
            "Default Product on error: " + throwable.getMessage(),
            BigDecimal.ZERO,
            Instant.now(),
            Instant.now()
        );
        return CompletableFuture.completedFuture(mapper.toDto(new Item(product, 0)));
    }

}
