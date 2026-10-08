package com.ecommerce.product.service.impl;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.entity.*;
import com.ecommerce.product.enums.ProductStatus;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.mapper.ProductMapper;
import com.ecommerce.product.repository.*;
import com.ecommerce.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {
	private final ProductRepository products;
	private final InventoryRepository inventories;
	private final ProductMapper mapper;

	@Override
	@Transactional
	public ProductResponse createProduct(ProductCreateRequest r) {
		String sku = mapper.normalizeSku(r.sku());
		if (products.existsBySku(sku))
			throw new DuplicateSkuException(sku);
		Product p = products.saveAndFlush(mapper.toEntity(r));
		inventories.save(new Inventory(p.getId(), r.initialQuantity()));
		log.info("Product created productId={}", p.getId());
		return mapper.toResponse(p);
	}

	@Override
	public ProductResponse getProductById(Long id) {
		return mapper.toResponse(findVisible(id));
	}

	@Override
	public PageResponse<ProductResponse> getAllProducts(Pageable pageable) {
		return PageResponse.from(products.findByStatusNot(ProductStatus.DELETED, pageable).map(mapper::toResponse));
	}

	@Override
	public PageResponse<ProductResponse> searchProducts(String keyword, Pageable pageable) {
		String escaped = keyword.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return PageResponse.from(products.search(escaped, pageable).map(mapper::toResponse));
	}

	@Override
	@Transactional
	public ProductResponse updateProduct(Long id, ProductUpdateRequest r) {
		Product p = findVisible(id);
		mapper.update(r, p);
		products.flush();
		log.info("Product updated productId={}", id);
		return mapper.toResponse(p);
	}

	@Override
	@Transactional
	public void deleteProduct(Long id) {
		Product p = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
		if (p.getStatus() != ProductStatus.DELETED) {
			p.delete();
			log.info("Product deleted productId={}", id);
		}
	}

	private Product findVisible(Long id) {
		return products.findById(id).filter(p -> p.getStatus() != ProductStatus.DELETED)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}
}
