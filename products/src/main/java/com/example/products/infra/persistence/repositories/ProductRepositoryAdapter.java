package com.example.products.infra.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.products.domain.model.Product;
import com.example.products.domain.spi.ProductRepository;
import com.example.products.infra.persistence.entities.ProductEntity;
import com.example.products.infra.persistence.mappers.ProductMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductMapper mapper;
    private final JpaRepository<ProductEntity, Long> jpaRepository;

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }
}
