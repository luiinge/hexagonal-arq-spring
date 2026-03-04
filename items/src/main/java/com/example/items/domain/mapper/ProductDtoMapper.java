package com.example.items.domain.mapper;

import com.example.items.domain.model.Product;
import com.example.items.dto.ProductDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductDtoMapper {

	Product toDomain(ProductDto productDto);

}
