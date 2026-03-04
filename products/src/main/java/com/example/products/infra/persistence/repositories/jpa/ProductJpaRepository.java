package com.example.products.infra.persistence.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.products.infra.persistence.entities.ProductEntity;

public interface ProductJpaRepository extends JpaRepository<ProductEntity, Long> {

}
