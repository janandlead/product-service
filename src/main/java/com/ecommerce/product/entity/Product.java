package com.ecommerce.product.entity;

import com.ecommerce.product.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(name = "uk_product_sku", columnNames = "sku"))
@Check(constraints = "price > 0")
@Getter
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 64) private String sku;
    @Column(nullable = false, length = 200) private String name;
    @Column(length = 2000) private String description;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private ProductStatus status;
    @Version private Long version;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(nullable = false) private LocalDateTime updatedAt;

    public Product(String sku, String name, String description, BigDecimal price) {
        this.sku = sku;
        update(name, description, price);
        this.status = ProductStatus.ACTIVE;
    }
    public void update(String name, String description, BigDecimal price) {
        if (name == null || name.isBlank() || price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Name and positive price are required");
        }
        this.name = name.trim();
        this.description = description;
        this.price = price;
    }
    public void delete() { this.status = ProductStatus.DELETED; }
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(ZoneOffset.UTC); updatedAt = createdAt; }
    @PreUpdate void onUpdate() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
}
