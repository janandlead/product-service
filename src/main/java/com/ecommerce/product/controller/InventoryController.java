package com.ecommerce.product.controller;

import com.ecommerce.product.dto.request.*;
import com.ecommerce.product.dto.response.*;
import com.ecommerce.product.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/api/inventory")
@Tag(name = "Inventory", description = "Trusted service inventory operations; adjustment requires ADMIN")
@RequiredArgsConstructor
public class InventoryController {
	private final InventoryService service;

	@GetMapping("/{productId}")
	@Operation(summary = "Get total, reserved and available stock")
	public InventoryResponse get(@PathVariable @Positive Long productId) {
		return service.getInventory(productId);
	}

	@PatchMapping("/{productId}/adjust")
	@Operation(summary = "Adjust stock by a nonzero signed quantity", description = "ADMIN only. New total must be at least reserved quantity.")
	public InventoryResponse adjust(@PathVariable @Positive Long productId,
			@Valid @RequestBody InventoryAdjustmentRequest request) {
		return service.adjustInventory(productId, request);
	}

	@PostMapping("/reserve")
	@Operation(summary = "Reserve all items atomically", description = "SERVICE role. Retry the exact same order and items safely. Changed payloads conflict. Returns current reservation status on replay.")
	public InventoryReservationResponse reserve(@Valid @RequestBody InventoryReservationRequest request) {
		return service.reserveInventory(request);
	}

	@PostMapping("/release")
	@Operation(summary = "Release an order reservation idempotently", description = "SERVICE role. Unknown orders and confirmed reservations return 409.")
	public InventoryReservationResponse release(@Valid @RequestBody OrderInventoryRequest request) {
		return service.releaseInventory(request.orderId());
	}

	@PostMapping("/confirm")
	@Operation(summary = "Deduct purchased stock idempotently", description = "SERVICE role. Unknown orders and released reservations return 409.")
	public InventoryReservationResponse confirm(@Valid @RequestBody OrderInventoryRequest request) {
		return service.confirmInventory(request.orderId());
	}
}
