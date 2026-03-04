package com.example.items.infra.clients;

import com.example.items.domain.spi.ProductService;
import com.example.items.dto.ProductDto;
import com.example.items.infra.clients.feign.ProductServiceFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceAdapter implements ProductService {

	private final ProductServiceFeignClient client;

	@Override
	public List<ProductDto> findAll() {
		return client.findAll();
	}

	@Override
	public ProductDto findById(Long id) {
		return client.findById(id);
	}

}
