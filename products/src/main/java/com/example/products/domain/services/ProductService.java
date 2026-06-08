package com.example.products.domain.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.commons.domain.exceptions.EntityNotFoundException;
import com.example.products.domain.model.Product;
import com.example.products.domain.spi.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public Product update(Product product) {
        if (product.getId() == null) {
            throw new IllegalArgumentException("Product id must not be null");
        }
        checkIfExists(product.getId());
        return productRepository.save(product);
    }

    @Transactional
    public void deleteById(Long id) {
        checkIfExists(id);
        productRepository.deleteById(id);
    }

    
    @Transactional
    public Product create(Product domain) {
        if (domain.getId() != null) {
            throw new IllegalArgumentException("New product id must be null");
        }
        return productRepository.save(domain);
    }


    private void checkIfExists(Long id) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product with id %s not found", id);
        }
    }
}
