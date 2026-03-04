package com.example.products.app.controllers;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.products.dto.ProductDto;
import com.example.products.app.mapper.ProductDtoMapper;
import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.products.domain.services.ProductService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final ProductDtoMapper productDtoMapper;

    @GetMapping
    public List<ProductDto> findAll() {
        return productService.findAll().stream().map(productDtoMapper::toDto).toList();
    }

    @GetMapping("/{id}")
    public ProductDto findById(@PathVariable Long id) {
        return productService.findById(id)
                .map(productDtoMapper::toDto)
                .orElseThrow(() -> new EntityNotFoundException("Product not found with id: %s", id));
    }

}
