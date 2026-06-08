package com.example.products.infra.persistence.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.example.products.domain.model.Product;
import com.example.products.domain.spi.ProductRepository;
import com.example.products.infra.persistence.entities.ProductEntity;
import com.example.products.infra.persistence.mappers.ProductMapper;
import com.example.products.infra.persistence.repositories.jpa.ProductJpaRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductRepositoryAdapter implements ProductRepository {

    private final ProductMapper mapper;
    private final ProductJpaRepository jpaRepository;

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = mapper.toEntity(product);
        ProductEntity savedEntity = jpaRepository.save(entity);
        return mapper.toDomain(savedEntity);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);    
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

}
