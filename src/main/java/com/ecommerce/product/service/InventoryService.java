package com.ecommerce.product.service;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;

public interface InventoryService {
	InventoryResponse getInventory(Long productId);

	InventoryResponse adjustInventory(Long productId, InventoryAdjustmentRequest request);

	InventoryReservationResponse reserveInventory(InventoryReservationRequest request);

	InventoryReservationResponse releaseInventory(Long orderId);

	InventoryReservationResponse confirmInventory(Long orderId);
}
