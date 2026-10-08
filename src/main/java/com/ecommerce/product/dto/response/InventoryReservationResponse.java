package com.ecommerce.product.dto.response;
import com.ecommerce.product.entity.InventoryReservation;
import com.ecommerce.product.enums.ReservationStatus;
import java.util.List;
public record InventoryReservationResponse(Long orderId, List<Item> items) {
    public record Item(Long productId, int quantity, ReservationStatus status) {}
    public static InventoryReservationResponse from(Long orderId, List<InventoryReservation> reservations) {
        return new InventoryReservationResponse(orderId, reservations.stream()
            .map(r -> new Item(r.getProductId(), r.getQuantity(), r.getStatus())).toList());
    }
}

