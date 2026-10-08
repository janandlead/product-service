package com.ecommerce.product.mapper;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.ProductResponse;
import com.ecommerce.product.entity.Product;
import org.springframework.stereotype.Component;
import java.util.Locale;

@Component
public class ProductMapper {
	public String normalizeSku(String sku) {
		return sku.trim().toUpperCase(Locale.ROOT);
	}

	public Product toEntity(ProductCreateRequest r) {
		return new Product(normalizeSku(r.sku()), r.name(), r.description(), r.price());
	}

	public ProductResponse toResponse(Product p) {
		return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(), p.getPrice(), p.getStatus(),
				p.getCreatedAt(), p.getUpdatedAt());
	}

	public void update(ProductUpdateRequest r, Product p) {
		p.update(r.name(), r.description(), r.price());
	}
}
