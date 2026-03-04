package com.example.items.infra.clients.feign;

import com.example.items.domain.model.Product;
import com.example.items.dto.ProductDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.util.List;

@FeignClient(name = "product-service", path = "/products")
public interface ProductServiceFeignClient {

	@GetMapping
	List<ProductDto> findAll();

	@GetMapping("/{id}")
	ProductDto findById(@PathVariable Long id);

}
