package com.ecommerce.product.exception;

public class ProductNotFoundException extends RuntimeException {
	public ProductNotFoundException(Long id) {
		super("Product " + id + " not found");
	}
}
