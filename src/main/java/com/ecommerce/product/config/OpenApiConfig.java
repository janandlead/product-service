package com.ecommerce.product.config;
import com.ecommerce.product.dto.response.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.*;
import java.util.Map;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI openAPI() {
        Components components = new Components();
        ModelConverters.getInstance().read(ErrorResponse.class).forEach(components::addSchemas);
        return new OpenAPI().info(new Info().title("Product Service").version("1.0.0")
            .description("Catalog and atomic inventory APIs."))
            .components(components);
    }
    @Bean OpenApiCustomizer examplesAndErrors() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, op) -> {
            if (method == PathItem.HttpMethod.POST && path.equals("/api/products")) {
                op.getResponses().remove("200");
                op.getResponses().addApiResponse("201", new ApiResponse().description("Product and inventory created")
                    .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ProductResponse")))));
            }
            for (var entry : Map.of("400", "Invalid request",
                    "404", "Product not found", "409", "Stock, state or concurrency conflict",
                    "500", "Unexpected server error").entrySet()) {
                String code = entry.getKey();
                op.getResponses().addApiResponse(code, new ApiResponse().description(entry.getValue())
                    .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))
                        .example(Map.of("timestamp", "2026-01-01T00:00:00Z", "status", Integer.parseInt(code),
                            "code", code.equals("409") ? "INSUFFICIENT_STOCK" : "ERROR",
                            "message", entry.getValue(), "path", path)))));
            }
            if (op.getRequestBody() != null && op.getRequestBody().getContent() != null) {
                Object example = path.endsWith("/reserve")
                    ? Map.of("orderId", 5001, "items", new Object[]{Map.of("productId", 1001, "quantity", 2), Map.of("productId", 1002, "quantity", 1)})
                    : path.endsWith("/release") || path.endsWith("/confirm") ? Map.of("orderId", 5001)
                    : path.endsWith("/adjust") ? Map.of("quantityChange", 50, "reason", "New stock received")
                    : method == PathItem.HttpMethod.POST
                        ? Map.of("sku", "MOBILE-001", "name", "Samsung Galaxy S25", "description", "5G Smartphone", "price", 74999.00, "initialQuantity", 100)
                        : Map.of("name", "Samsung Galaxy S25 Ultra", "description", "Updated Smartphone", "price", 84999.00);
                op.getRequestBody().getContent().values().forEach(media -> media.setExample(example));
            }
            op.getResponses().forEach((status, response) -> {
                if (!status.startsWith("2") || response.getContent() == null) return;
                Object example;
                if (path.contains("/inventory")) {
                    if (path.endsWith("/reserve") || path.endsWith("/release") || path.endsWith("/confirm")) {
                        String state = path.endsWith("/release") ? "RELEASED" : path.endsWith("/confirm") ? "CONFIRMED" : "RESERVED";
                        example = Map.of("orderId", 5001, "items", new Object[]{
                            Map.of("productId", 1001, "quantity", 2, "status", state)});
                    } else {
                        example = Map.of("productId", 1001, "totalQuantity", 100, "reservedQuantity", 20, "availableQuantity", 80);
                    }
                } else if (method == PathItem.HttpMethod.GET && !path.contains("{")) {
                    example = Map.of("content", new Object[]{productExample()}, "page", 0, "size", 10,
                        "totalElements", 1, "totalPages", 1, "last", true);
                } else {
                    example = productExample();
                }
                response.getContent().values().forEach(media -> media.setExample(example));
            });
        }));
    }
    private Map<String, Object> productExample() {
        return Map.of("id", 1001, "sku", "MOBILE-001", "name", "Samsung Galaxy S25",
            "description", "5G Smartphone", "price", 74999.00, "status", "ACTIVE",
            "createdAt", "2026-01-01T00:00:00", "updatedAt", "2026-01-01T00:00:00");
    }
}
