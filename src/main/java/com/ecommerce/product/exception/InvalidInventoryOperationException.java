package com.ecommerce.product.exception;

public class InvalidInventoryOperationException extends RuntimeException {
	public InvalidInventoryOperationException(String message) {
		super(message);
	}
}
