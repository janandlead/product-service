package com.ecommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String sku,
		@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal price,
		@NotNull @PositiveOrZero Integer initialQuantity) {

}
