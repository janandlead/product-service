package com.ecommerce.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record ProductUpdateRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description,
		@NotNull @DecimalMin(value = "0.0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal price) {

}
