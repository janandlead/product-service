package com.ecommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record InventoryReservationRequest(@NotNull @Positive Long orderId,
		@NotEmpty @Size(max = 100) List<@NotNull @Valid Item> items) {
	public record Item(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {
	}
}
