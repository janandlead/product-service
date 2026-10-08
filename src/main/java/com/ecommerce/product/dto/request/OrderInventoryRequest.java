package com.ecommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record OrderInventoryRequest(@NotNull @Positive Long orderId) {

}
