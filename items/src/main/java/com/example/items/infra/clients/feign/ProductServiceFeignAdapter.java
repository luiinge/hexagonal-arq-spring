package com.example.items.infra.clients.feign;

import com.example.items.domain.spi.ProductService;
import com.example.items.dto.ProductDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "clients.products.type", havingValue = "feign", matchIfMissing = true)
public class ProductServiceFeignAdapter implements ProductService {

	private final ProductServiceFeignClient feignClient;


	@Override
	public List<ProductDto> findAll() {
		return feignClient.findAll();
	}

	@Override
	public ProductDto findById(Long id) {
		return feignClient.findById(id);
	}

}
