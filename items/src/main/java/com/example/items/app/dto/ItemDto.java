package com.example.items.app.dto;

import com.example.items.dto.ProductDto;

public record ItemDto(
	ProductDto product,
	Integer quantity
) {
}
