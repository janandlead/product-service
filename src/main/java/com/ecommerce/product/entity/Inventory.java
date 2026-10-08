package com.ecommerce.product.entity;

import com.ecommerce.product.exception.InvalidInventoryOperationException;
import com.ecommerce.product.exception.InsufficientStockException;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "inventory", uniqueConstraints = @UniqueConstraint(name = "uk_inventory_product", columnNames = "product_id"))
@Check(constraints = "total_quantity >= 0 and reserved_quantity >= 0 and reserved_quantity <= total_quantity")
public class Inventory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "product_id", nullable = false, updatable = false) private Long productId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_inventory_product"))
    private Product product;
    @Column(name = "total_quantity", nullable = false) private Integer totalQuantity;
    @Column(name = "reserved_quantity", nullable = false) private Integer reservedQuantity;
    @Version private Long version;
    @Column(nullable = false) private LocalDateTime updatedAt;
    protected Inventory() {}
    public Inventory(Long productId, int quantity) {
        if (quantity < 0) throw new InvalidInventoryOperationException("Initial stock cannot be negative");
        this.productId = productId; this.totalQuantity = quantity; this.reservedQuantity = 0;
    }
    public void adjust(int change) {
        long result = (long) totalQuantity + change;
        if (change == 0 || result < reservedQuantity || result > Integer.MAX_VALUE)
            throw new InvalidInventoryOperationException("Adjustment must be nonzero and keep total between reserved stock and 2147483647");
        totalQuantity = (int) result;
    }
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (quantity > getAvailableQuantity()) throw new InsufficientStockException(productId, getAvailableQuantity(), quantity);
        reservedQuantity += quantity;
    }
    public void release(int quantity) { requireReserved(quantity); reservedQuantity -= quantity; }
    public void confirm(int quantity) { requireReserved(quantity); reservedQuantity -= quantity; totalQuantity -= quantity; }
    private void requirePositive(int quantity) {
        if (quantity <= 0) throw new InvalidInventoryOperationException("Quantity must be positive");
    }
    private void requireReserved(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) throw new InvalidInventoryOperationException("Reservation counters are inconsistent");
    }
    @PrePersist @PreUpdate void timestamp() { updatedAt = LocalDateTime.now(ZoneOffset.UTC); }
    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public Integer getTotalQuantity() { return totalQuantity; }
    public Integer getReservedQuantity() { return reservedQuantity; }
    public int getAvailableQuantity() { return totalQuantity - reservedQuantity; }
    public Long getVersion() { return version; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
