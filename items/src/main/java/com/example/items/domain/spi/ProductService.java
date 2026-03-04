package com.example.items.domain.spi;

import com.example.items.dto.ProductDto;
import java.util.List;

public interface ProductService {

	List<ProductDto> findAll();
	ProductDto findById(Long id);

}
