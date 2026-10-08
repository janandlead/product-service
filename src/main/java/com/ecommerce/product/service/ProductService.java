package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;
import org.springframework.data.domain.Pageable;

public interface ProductService {
	ProductResponse createProduct(ProductCreateRequest request);

	ProductResponse getProductById(Long id);

	PageResponse<ProductResponse> getAllProducts(Pageable pageable);

	PageResponse<ProductResponse> searchProducts(String keyword, Pageable pageable);

	ProductResponse updateProduct(Long id, ProductUpdateRequest request);

	void deleteProduct(Long id);
}
