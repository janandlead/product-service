package com.ecommerce.product.exception;

public class InsufficientStockException extends RuntimeException {
	private final int availableQuantity;
	private final int requestedQuantity;

	public InsufficientStockException(Long id, int available, int requested) {
		super("Insufficient stock for product " + id);
		this.availableQuantity = available;
		this.requestedQuantity = requested;
	}

	public int getAvailableQuantity() {
		return availableQuantity;
	}

	public int getRequestedQuantity() {
		return requestedQuantity;
	}
}
