package com.example.items.app.controller;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.items.app.dto.ItemDto;
import com.example.items.app.mapper.ItemDtoMapper;
import com.example.items.domain.services.ItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/items")
@RequiredArgsConstructor
public class ItemController {

	private final ItemService itemService;
	private final ItemDtoMapper mapper;

	@GetMapping
	public List<ItemDto> findAll() {
		return itemService.findAll().stream()
			.map(mapper::toDto)
			.toList();
	}

	@GetMapping("/{id}")
	public ItemDto findById(@PathVariable Long id) {
		return itemService.findById(id)
			.map(mapper::toDto)
			.orElseThrow(() -> new EntityNotFoundException("Item not found with id: %s", id));
	}

}
