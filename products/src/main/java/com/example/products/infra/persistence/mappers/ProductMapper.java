package com.example.products.infra.persistence.mappers;

import com.example.products.domain.model.Product;
import com.example.products.infra.persistence.entities.ProductEntity;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    ProductMapper INSTANCE = Mappers.getMapper(ProductMapper.class);

    Product toDomain(ProductEntity entity);

    ProductEntity toEntity(Product product);
}
