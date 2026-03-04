package com.example.items.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.Instant;

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
