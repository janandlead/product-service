package com.ecommerce.product.service.impl;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.entity.*;
import com.ecommerce.product.enums.*;
import com.ecommerce.product.exception.*;
import com.ecommerce.product.repository.*;
import com.ecommerce.product.service.InventoryService;
import org.slf4j.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class InventoryServiceImpl implements InventoryService {
	private static final Logger log = LoggerFactory.getLogger(InventoryServiceImpl.class);
	private final ProductRepository products;
	private final InventoryRepository inventories;
	private final InventoryReservationRepository reservations;
	private final OrderTransactionLock orderLock;

	public InventoryServiceImpl(ProductRepository products, InventoryRepository inventories,
			InventoryReservationRepository reservations, OrderTransactionLock orderLock) {
		this.products = products;
		this.inventories = inventories;
		this.reservations = reservations;
		this.orderLock = orderLock;
	}

	@Override
	public InventoryResponse getInventory(Long productId) {
		return InventoryResponse.from(inventory(productId));
	}

	@Override
	@Transactional
	public InventoryResponse adjustInventory(Long productId, InventoryAdjustmentRequest request) {
		Inventory i = inventory(productId);
		i.adjust(request.quantityChange());
		inventories.flush();
		log.info("Inventory adjusted productId={} change={}", productId, request.quantityChange());
		return InventoryResponse.from(i);
	}

	@Override
	@Transactional
	public InventoryReservationResponse reserveInventory(InventoryReservationRequest request) {
		Map<Long, Integer> requested = new TreeMap<>();
		for (InventoryReservationRequest.Item item : request.items()) {
			if (requested.putIfAbsent(item.productId(), item.quantity()) != null)
				throw new IllegalArgumentException("Duplicate product IDs are not allowed");
		}
		orderLock.acquire(request.orderId());
		List<InventoryReservation> existing = reservations.findByOrderId(request.orderId());
		if (!existing.isEmpty()) {
			Map<Long, Integer> original = new TreeMap<>();
			existing.forEach(r -> original.put(r.getProductId(), r.getQuantity()));
			if (!original.equals(requested))
				throw new InvalidInventoryOperationException(
						"Order ID already used with different items or quantities");
			// Return current state even after confirmation/release; never reserve twice.
			return InventoryReservationResponse.from(request.orderId(), existing);
		}
		List<InventoryReservation> created = new ArrayList<>();
		requested.forEach((productId, quantity) -> {
			Product p = products.findForReservation(productId)
					.orElseThrow(() -> new ProductNotFoundException(productId));
			if (p.getStatus() != ProductStatus.ACTIVE)
				throw new InvalidInventoryOperationException("Product " + productId + " is not active");
			Inventory i = inventory(productId);
			i.reserve(quantity);
			created.add(new InventoryReservation(request.orderId(), productId, quantity));
		});
		// Dirty checking writes inventory using WHERE id=? AND version=?.
		// Any conflict rolls back ALL inventory changes and reservation inserts.
		inventories.flush();
		reservations.saveAllAndFlush(created);
		log.info("Inventory reserved orderId={} items={}", request.orderId(), created.size());
		return InventoryReservationResponse.from(request.orderId(), created);
	}

	@Override
	@Transactional
	public InventoryReservationResponse releaseInventory(Long orderId) {
		return complete(orderId, ReservationStatus.RELEASED);
	}

	@Override
	@Transactional
	public InventoryReservationResponse confirmInventory(Long orderId) {
		return complete(orderId, ReservationStatus.CONFIRMED);
	}

	private InventoryReservationResponse complete(Long orderId, ReservationStatus target) {
		orderLock.acquire(orderId);
		List<InventoryReservation> found = reservations.findByOrderId(orderId);
		if (found.isEmpty())
			throw new InvalidInventoryOperationException("No reservation exists for order " + orderId);
		for (InventoryReservation r : found) {
			if (r.getStatus() == target)
				continue;
			if (r.getStatus() != ReservationStatus.RESERVED)
				throw new InvalidInventoryOperationException(
						"Cannot change a " + r.getStatus() + " reservation to " + target);
			// Existing reservations must remain completable after product soft deletion.
			Inventory i = inventory(r.getProductId());
			if (target == ReservationStatus.CONFIRMED)
				i.confirm(r.getQuantity());
			else
				i.release(r.getQuantity());
			r.complete(target);
		}
		inventories.flush();
		reservations.flush();
		log.info("Inventory {} orderId={}", target, orderId);
		return InventoryReservationResponse.from(orderId, found);
	}

	private Inventory inventory(Long productId) {
		return inventories.findByProductId(productId).orElseThrow(() -> new ProductNotFoundException(productId));
	}
}
