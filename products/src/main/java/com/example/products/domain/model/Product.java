package com.example.products.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class Product {

    private final Long id;
    private final String name;
    private final BigDecimal price;
    private final Instant createdAt;
    private final Instant updatedAt;

}
