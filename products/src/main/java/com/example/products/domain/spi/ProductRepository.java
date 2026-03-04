package com.example.products.domain.spi;

import java.util.List;
import java.util.Optional;

import com.example.products.domain.model.Product;

public interface ProductRepository {

    List<Product> findAll();

    Optional<Product> findById(Long id);

}
