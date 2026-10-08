package com.ecommerce.product.repository;

import com.ecommerce.product.entity.InventoryReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
	List<InventoryReservation> findByOrderIdOrderByProductId(Long orderId);

	default List<InventoryReservation> findByOrderId(Long orderId) {
		return findByOrderIdOrderByProductId(orderId);
	}

	Optional<InventoryReservation> findByOrderIdAndProductId(Long orderId, Long productId);
}
