package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products", description = "Public catalog browsing; ADMIN manages products")
@RequiredArgsConstructor
public class ProductController {
	private final ProductService service;
	private static final Set<String> SORT_FIELDS = Set.of("id", "sku", "name", "price", "createdAt", "updatedAt");

	@PostMapping
	@Operation(summary = "Create product and initial inventory atomically")
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
		ProductResponse response = service.createProduct(request);
		return ResponseEntity.created(URI.create("/api/products/" + response.id())).body(response);
	}

	@GetMapping("/{productId}")
	@Operation(summary = "Get a non-deleted product")
	public ProductResponse get(@PathVariable @Positive Long productId) {
		return service.getProductById(productId);
	}

	@GetMapping
	@Operation(summary = "Browse non-deleted products", description = "Page starts at 0; size 1–100. sort: id, sku, name, price, createdAt, updatedAt followed by ,asc or ,desc.")
	public PageResponse<ProductResponse> list(@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "id,asc") String sort) {
		return service.getAllProducts(pageable(page, size, sort));
	}

	@GetMapping("/search")
	@Operation(summary = "Search product name and SKU, case-insensitively")
	public PageResponse<ProductResponse> search(@RequestParam @NotBlank @Size(max = 200) String keyword,
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
			@RequestParam(defaultValue = "id,asc") String sort) {
		return service.searchProducts(keyword, pageable(page, size, sort));
	}

	@PutMapping("/{productId}")
	@Operation(summary = "Update product details; SKU and stock are immutable here")
	public ProductResponse update(@PathVariable @Positive Long productId,
			@Valid @RequestBody ProductUpdateRequest request) {
		return service.updateProduct(productId, request);
	}

	@DeleteMapping("/{productId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Operation(summary = "Soft-delete a product")
	public void delete(@PathVariable @Positive Long productId) {
		service.deleteProduct(productId);
	}

	private Pageable pageable(int page, int size, String sort) {
		String[] parts = sort.split(",", -1);
		if (parts.length != 2 || !SORT_FIELDS.contains(parts[0]))
			throw new IllegalArgumentException("sort must be an allowed field followed by ,asc or ,desc");
		Sort ordering = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
		if (!parts[0].equals("id"))
			ordering = ordering.and(Sort.by("id"));
		return PageRequest.of(page, size, ordering);
	}
}
