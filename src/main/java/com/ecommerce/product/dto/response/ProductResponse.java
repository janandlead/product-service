package com.ecommerce.product.dto.response;
import com.ecommerce.product.enums.ProductStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public record ProductResponse(Long id, String sku, String name, String description, BigDecimal price,
    ProductStatus status, LocalDateTime createdAt, LocalDateTime updatedAt) {}

