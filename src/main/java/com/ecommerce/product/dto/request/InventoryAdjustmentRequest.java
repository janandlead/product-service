package com.ecommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record InventoryAdjustmentRequest(@NotNull Integer quantityChange, @NotBlank @Size(max = 500) String reason) {

}
