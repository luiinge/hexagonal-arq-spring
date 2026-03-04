package com.example.products.app.mapper;

import org.mapstruct.Mapper;

import com.example.products.dto.ProductDto;
import com.example.products.domain.model.Product;

@Mapper(componentModel = "spring")
public interface ProductDtoMapper {

    ProductDto toDto(Product product);

    Product toDomain(ProductDto productDto);

}
