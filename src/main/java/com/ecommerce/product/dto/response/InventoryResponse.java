package com.ecommerce.product.dto.response;
import com.ecommerce.product.entity.Inventory;
public record InventoryResponse(Long productId, int totalQuantity, int reservedQuantity, int availableQuantity) {
    public static InventoryResponse from(Inventory i) {
        return new InventoryResponse(i.getProductId(), i.getTotalQuantity(), i.getReservedQuantity(), i.getAvailableQuantity());
    }
}

