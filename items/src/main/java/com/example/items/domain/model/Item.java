package com.example.items.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
public class Item {

	private Product product;
	private Integer quantity;

	public BigDecimal total() {
		return product.getPrice().multiply(BigDecimal.valueOf(quantity));
	}

}
