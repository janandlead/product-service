package com.ecommerce.product.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(Instant timestamp, int status, String code, String message, String path,
    Integer availableQuantity, Integer requestedQuantity, Map<String, String> fieldErrors) {}

