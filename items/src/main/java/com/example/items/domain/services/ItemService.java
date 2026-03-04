package com.example.items.domain.services;

import com.example.items.domain.mapper.ProductDtoMapper;
import com.example.items.domain.model.Item;
import com.example.items.domain.model.Product;
import com.example.items.domain.spi.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ItemService {

	private final ProductService productService;
	private final ProductDtoMapper mapper;

	public List<Item> findAll() {
		return productService.findAll().stream()
			.map(mapper::toDomain)
			.map(product -> new Item(product, 1))
			.toList();
	}


	public Optional<Item> findById(Long id) {
		return Optional.ofNullable(productService.findById(id))
			.map(mapper::toDomain)
			.map(product -> new Item(product, 1));
	}

}
